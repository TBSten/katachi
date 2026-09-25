package me.tbsten.katachi.test.architecture

import com.lemonappdev.konsist.api.declaration.KoBaseDeclaration
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import com.lemonappdev.konsist.api.provider.KoAnnotationProvider
import com.lemonappdev.konsist.api.provider.KoNameProvider
import com.lemonappdev.konsist.api.provider.KoTextProvider
import com.lemonappdev.konsist.api.provider.modifier.KoVisibilityModifierProvider

/**
 * The name every library role declares the internal-package rule under.
 *
 * One rule for both directions rather than two, so that each role carries one more line instead
 * of two, and the report's wording says both halves at once. The `konsist { }` call stays in each
 * role's own file for the reason `LayerImports.kt` gives.
 */
const val INTERNAL_PACKAGE_RULE: String =
    "internal / @InternalKatachiApi のトップレベル宣言は .internal パッケージに、それ以外はその外に置くこと"

/** The annotation that marks a public declaration as internal to the library. */
private const val INTERNAL_API_ANNOTATION: String = "InternalKatachiApi"

/** Whether [file] declares a package named `….internal` or `….internal.…`. */
fun isInInternalPackage(file: KoFileDeclaration): Boolean =
    file.packagee?.name?.let { it.endsWith(".internal") || ".internal." in it } == true

/**
 * The top-level declarations of [file] that sit on the wrong side of the `.internal` line.
 *
 * - **Only top level.** A member cannot move to another package apart from its type, so an
 *   `internal constructor` or an `@property:InternalKatachiApi val` of a public type stays with it.
 * - **`private` is on neither side.** It is closed inside its file and goes wherever its users go.
 * - **Direct subtypes of a sealed type in [sealedParents] may stay outside.** Kotlin requires a
 *   sealed type's direct subtypes to share its package, so `ArchitectureScopeImpl` and its kind
 *   have nowhere else to be. The exemption applies only to that direction: a public declaration
 *   inside `.internal` is still reported whatever its parents are.
 *
 * `KoVisibilityModifierProvider` keeps out what carries no visibility — imports, the package
 * directive, file annotations.
 */
fun misplacedDeclarationsOf(file: KoFileDeclaration, sealedParents: Set<String>): List<KoBaseDeclaration> {
    val inInternalPackage = isInInternalPackage(file)
    return file.declarations(includeNested = false, includeLocal = false)
        .filter { it is KoVisibilityModifierProvider }
        .filterNot { (it as? KoVisibilityModifierProvider)?.hasPrivateModifier == true }
        .filterNot { !inInternalPackage && isDirectSubtypeOfAny(it, sealedParents) }
        .filter { isInternalApi(it) != inInternalPackage }
}

/** Whether [declaration] is `internal` or opted into `@InternalKatachiApi`. */
private fun isInternalApi(declaration: KoBaseDeclaration): Boolean =
    (declaration as? KoVisibilityModifierProvider)?.hasInternalModifier == true ||
        (declaration as? KoAnnotationProvider)?.hasAnnotationWithName(INTERNAL_API_ANNOTATION) == true

/** Whether one of the direct supertypes of [declaration] is named in [names]. */
private fun isDirectSubtypeOfAny(declaration: KoBaseDeclaration, names: Set<String>): Boolean =
    directSupertypeNamesOf(declaration).any { it in names }

/**
 * The simple names of the supertypes [declaration] lists after its `:`, read off its own text.
 *
 * Not `KoParentProvider.parents()`, and deliberately so. Konsist 0.17.3 ends that call with
 * `distinctBy { it.sourceDeclaration }`, and resolving a source declaration searches a scope of
 * the whole project Konsist infers — here the whole repository, samples included — which ran the
 * test JVM out of memory. The header is short and regular enough to read directly: skip to the
 * declaration's own name, step over its type parameters and primary constructor, and take what
 * the `:` lists up to the body or a `where` clause.
 */
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

/**
 * What follows the header's `:` in [rest], the text after a declaration's name, or `null` when it
 * lists no supertype. Brackets are skipped so a constructor parameter's `:` is never taken.
 */
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

/** Whether the `>` at [index] closes a `<`, rather than ending the arrow of a function type. */
private fun isClosingAngle(text: String, index: Int): Boolean =
    text[index] == '>' && text.getOrNull(index - 1) != '-'

/** [list] split at the commas that are not inside `<…>` or `(…)`. */
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
