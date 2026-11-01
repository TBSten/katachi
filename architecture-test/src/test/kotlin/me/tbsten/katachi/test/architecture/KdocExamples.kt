package me.tbsten.katachi.test.architecture

import com.lemonappdev.konsist.api.declaration.KoBaseDeclaration
import com.lemonappdev.konsist.api.declaration.KoClassDeclaration
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import com.lemonappdev.konsist.api.declaration.KoFunctionDeclaration
import com.lemonappdev.konsist.api.declaration.KoInterfaceDeclaration
import com.lemonappdev.konsist.api.declaration.KoObjectDeclaration
import com.lemonappdev.konsist.api.provider.KoContainingDeclarationProvider
import com.lemonappdev.konsist.api.provider.KoContainingFileProvider
import com.lemonappdev.konsist.api.provider.KoIsConstructorDefinedProvider
import com.lemonappdev.konsist.api.provider.KoKDocProvider
import com.lemonappdev.konsist.api.provider.KoLocationProvider
import com.lemonappdev.konsist.api.provider.KoPathProvider
import com.lemonappdev.konsist.api.provider.modifier.KoOverrideModifierProvider
import com.lemonappdev.konsist.api.provider.modifier.KoVisibilityModifierProvider

const val KDOC_EXAMPLE_RULE: String = """public な宣言に KDoc があり、型とトップレベル関数の KDoc は "## Example" を含むこと"""

const val KDOC_TAG_ORDER_RULE: String =
    """public な宣言の KDoc で、ブロックタグ（@param 等）より後ろに見出しや本文を置かないこと"""

private const val EXAMPLE_HEADING: String = "## Example"

private const val CODE_FENCE: String = "```"

private const val MAX_NESTING: Int = 16

fun publicDeclarationsOf(file: KoFileDeclaration): List<KoBaseDeclaration> = file
    .declarations(includeNested = true, includeLocal = false)
    .filterNot { (it as? KoIsConstructorDefinedProvider)?.isConstructorDefined == true }
    .filterNot { (it as? KoOverrideModifierProvider)?.hasOverrideModifier == true }
    .filter { it is KoKDocProvider && isReachablePublic(it) }

fun showsExample(declaration: KoBaseDeclaration): Boolean {
    val kDoc = kDocOf(declaration) ?: return false
    return !needsExample(declaration) || kDoc.contains(EXAMPLE_HEADING)
}

private fun needsExample(declaration: KoBaseDeclaration): Boolean = when (declaration) {
    is KoClassDeclaration, is KoInterfaceDeclaration, is KoObjectDeclaration -> true
    is KoFunctionDeclaration -> declaration.isTopLevel
    else -> false
}

fun keepsBlockTagsLast(declaration: KoBaseDeclaration): Boolean =
    kDocOf(declaration)?.let { hasNothingAfterItsBlockTags(it) } != false

private fun hasNothingAfterItsBlockTags(kDocText: String): Boolean {
    val lines = kDocText.lines()
    val bodyLines = lines.mapIndexed { index, raw ->
        var line = raw
        if (index == 0) line = line.substringAfter("/**", line)
        if (index == lines.lastIndex) line = line.substringBeforeLast("*/", line)
        val trimmed = line.trim()
        if (trimmed.startsWith("*")) trimmed.removePrefix("*").trim() else trimmed
    }

    var inFence = false
    var paragraphStarted = false
    var isTagParagraph = false
    var sawTagParagraph = false
    for (line in bodyLines) {
        val trimmed = line.trim()
        if (trimmed.startsWith(CODE_FENCE)) {
            inFence = !inFence
            continue
        }
        if (inFence) continue
        if (trimmed.isEmpty()) {
            paragraphStarted = false
            continue
        }
        if (!paragraphStarted) {
            paragraphStarted = true
            isTagParagraph = trimmed.startsWith("@")
            if (isTagParagraph) {
                sawTagParagraph = true
            } else if (sawTagParagraph) {
                return false
            }
        }
    }
    return true
}

private fun kDocOf(declaration: KoBaseDeclaration): String? =
    (declaration as? KoKDocProvider)?.kDoc?.text ?: kDocAbove(declaration)

private fun kDocAbove(declaration: KoBaseDeclaration): String? {
    val lines = (declaration as? KoContainingFileProvider)?.containingFile?.text?.lines() ?: return null
    val line = lineOf(declaration) ?: return null

    var cursor = line - 2
    while (cursor >= 0 && lines[cursor].trim().let(::isBetweenKDocAndDeclaration)) cursor--
    if (cursor < 0 || !lines[cursor].trim().endsWith("*/")) return null

    val end = cursor
    while (cursor >= 0) {
        val trimmed = lines[cursor].trim()
        if (trimmed.startsWith("/**")) return lines.subList(cursor, end + 1).joinToString("\n")
        // A plain `/* … */` is a comment and not a KDoc, so the block that ended above the
        // declaration documents nothing and the search must not walk past it to an older one.
        if (trimmed.startsWith("/*")) return null
        cursor--
    }
    return null
}

private fun isBetweenKDocAndDeclaration(trimmed: String): Boolean =
    trimmed.isEmpty() || trimmed.startsWith("@") || trimmed.startsWith("//") || trimmed.startsWith("context(")

private fun lineOf(declaration: KoBaseDeclaration): Int? {
    val path = (declaration as? KoPathProvider)?.path ?: return null
    val location = (declaration as? KoLocationProvider)?.let { runCatching { it.location }.getOrNull() } ?: return null
    val rest = location.removePrefix("$path:")
    if (rest == location) return null
    return rest.substringBefore(':').toIntOrNull()
}

private fun isReachablePublic(declaration: KoBaseDeclaration): Boolean {
    val visibility = declaration as? KoVisibilityModifierProvider ?: return false
    if (!visibility.hasPublicOrDefaultModifier) return false

    var current: KoBaseDeclaration = declaration
    repeat(MAX_NESTING) {
        val outer = (current as? KoContainingDeclarationProvider)?.containingDeclaration ?: return true
        if (outer is KoFileDeclaration) return true
        val outerVisibility = outer as? KoVisibilityModifierProvider ?: return true
        if (!outerVisibility.hasPublicOrDefaultModifier) return false
        current = outer
    }
    return true
}
