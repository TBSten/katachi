package me.tbsten.katachi.test.architecture

import com.lemonappdev.konsist.api.declaration.KoBaseDeclaration
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import com.lemonappdev.konsist.api.provider.KoAnnotationProvider
import com.lemonappdev.konsist.api.provider.KoNameProvider
import com.lemonappdev.konsist.api.provider.KoTextProvider
import com.lemonappdev.konsist.api.provider.modifier.KoVisibilityModifierProvider

const val INTERNAL_PACKAGE_RULE: String =
    "internal / @InternalKatachiApi のトップレベル宣言は .internal パッケージに、それ以外はその外に置くこと"

private const val INTERNAL_API_ANNOTATION: String = "InternalKatachiApi"

fun isInInternalPackage(file: KoFileDeclaration): Boolean =
    file.packagee?.name?.let { it.endsWith(".internal") || ".internal." in it } == true

fun misplacedDeclarationsOf(file: KoFileDeclaration, sealedParents: Set<String>): List<KoBaseDeclaration> {
    val inInternalPackage = isInInternalPackage(file)
    return file.declarations(includeNested = false, includeLocal = false)
        .filter { it is KoVisibilityModifierProvider }
        .filterNot { (it as? KoVisibilityModifierProvider)?.hasPrivateModifier == true }
        .filterNot { !inInternalPackage && isDirectSubtypeOfAny(it, sealedParents) }
        .filter { isInternalApi(it) != inInternalPackage }
}

private fun isInternalApi(declaration: KoBaseDeclaration): Boolean =
    (declaration as? KoVisibilityModifierProvider)?.hasInternalModifier == true ||
        (declaration as? KoAnnotationProvider)?.hasAnnotationWithName(INTERNAL_API_ANNOTATION) == true

private fun isDirectSubtypeOfAny(declaration: KoBaseDeclaration, names: Set<String>): Boolean =
    directSupertypeNamesOf(declaration).any { it in names }

// Read off the declaration's text, not `parents()`: that resolves source declarations across the whole
// repository (samples included) and ran the test JVM out of memory.
private fun directSupertypeNamesOf(declaration: KoBaseDeclaration): List<String> {
    val name = (declaration as? KoNameProvider)?.name ?: return emptyList()
    val text = (declaration as? KoTextProvider)?.text ?: return emptyList()
    val header = Regex("""\b(?:class|interface|object)\s+${Regex.escape(name)}\b""").find(text)
        ?: return emptyList()
    val supertypes = supertypeListOf(text.substring(header.range.last + 1)) ?: return emptyList()
    return splitAtTopLevelCommas(supertypes).map { entry ->
        entry.trim().takeWhile { it.isLetterOrDigit() || it == '_' || it == '.' }.substringAfterLast('.')
    }
}

private fun supertypeListOf(rest: String): String? {
    var depth = 0
    var start = -1
    for ((index, char) in rest.withIndex()) {
        when {
            char == '(' || char == '<' -> depth++
            char == ')' || isClosingAngle(rest, index) -> depth--
            depth > 0 -> Unit
            char == '{' || rest.startsWith(" where ", index) -> return if (start < 0) null else rest.substring(start, index)
            char == ':' && start < 0 -> start = index + 1
        }
    }
    return if (start < 0) null else rest.substring(start)
}

private fun isClosingAngle(text: String, index: Int): Boolean =
    text[index] == '>' && text.getOrNull(index - 1) != '-'

private fun splitAtTopLevelCommas(list: String): List<String> {
    val entries = mutableListOf<String>()
    var depth = 0
    var from = 0
    for ((index, char) in list.withIndex()) {
        when (char) {
            '(', '<' -> depth++
            ')' -> depth--
            '>' -> if (isClosingAngle(list, index)) depth--
            ',' -> if (depth == 0) {
                entries += list.substring(from, index)
                from = index + 1
            }
        }
    }
    entries += list.substring(from)
    return entries
}
