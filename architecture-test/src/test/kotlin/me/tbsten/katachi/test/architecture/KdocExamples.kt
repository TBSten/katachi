package me.tbsten.katachi.test.architecture

import com.lemonappdev.konsist.api.declaration.KoBaseDeclaration
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import com.lemonappdev.konsist.api.provider.KoContainingDeclarationProvider
import com.lemonappdev.konsist.api.provider.KoContainingFileProvider
import com.lemonappdev.konsist.api.provider.KoIsConstructorDefinedProvider
import com.lemonappdev.konsist.api.provider.KoKDocProvider
import com.lemonappdev.konsist.api.provider.KoLocationProvider
import com.lemonappdev.konsist.api.provider.KoPathProvider
import com.lemonappdev.konsist.api.provider.modifier.KoOverrideModifierProvider
import com.lemonappdev.konsist.api.provider.modifier.KoVisibilityModifierProvider

/**
 * The name every role declares the KDoc rule under.
 *
 * Shared so that every role declaring it prints one sentence rather than its own spelling of it,
 * in the same way `laterLayersOf` is shared. The `konsist { }` call itself stays in each role's own file —
 * see `LayerImports.kt` for why it may not be wrapped in a shared one.
 */
const val KDOC_EXAMPLE_RULE: String = """public な宣言に "## Example" を含む KDoc があること"""

/**
 * The name every role declares the block-tag-order rule under.
 *
 * Dokka stops rendering a KDoc's body at its first block tag (`@param`, `@property`, `@return`,
 * `@throws`, `@see`, `@sample`, `@since`, `@featured`, `@llm`, ...) — anything after that,
 * including a later `## Example`, is folded into the tag section instead of the page body and
 * renders broken. The repository's own convention (`docs/internal/kotlin/kotlin.md`) is 1行サマリ
 * → 詳細 → `## Example` → ブロックタグ, in that order, so this rule is what keeps a KDoc from
 * silently drifting out of an order Dokka can render.
 */
const val KDOC_TAG_ORDER_RULE: String =
    """public な宣言の KDoc で、ブロックタグ（@param 等）より後ろに見出しや本文を置かないこと"""

/** What a KDoc has to hold. The number and the title after it are the example's own. */
private const val EXAMPLE_HEADING: String = "## Example"

/** How a KDoc line opens a fenced code block, inside which `@` and `#` count for nothing. */
private const val CODE_FENCE: String = "```"

/**
 * How far up the containing chain the reachability walk goes before it gives up and answers
 * "reachable". Kotlin nesting never comes close; the bound is here so a cycle in Konsist's
 * `containingDeclaration` could not hang the check.
 */
private const val MAX_NESTING: Int = 16

/**
 * The declarations of [file] the KDoc rule is about: what a reader of the library can reach and
 * has to be shown how to use.
 *
 * Four narrowings, and only the first is the repository's own wording.
 *
 * - **Public.** `hasPublicOrDefaultModifier`, so `@InternalKatachiApi` is included — it is
 *   `public` and the rule says so in as many words.
 * - **Not `override`.** An override inherits its documentation; `equals`, `hashCode` and
 *   `toString` are half of them here, and an example on each would say the same thing about
 *   Kotlin rather than anything about katachi.
 * - **Reachable.** `kotlin.md` asks for KDoc on what is *public*, and a `public` member of an
 *   `internal class` is not: the modifier is there because an interface implementation cannot
 *   narrow it, not because anyone outside the module can call it. So the question asked is the
 *   visibility of every type the declaration sits inside, not the declaration's own.
 * - **Not a `val` of a primary constructor.** Konsist answers such a property with its class's
 *   KDoc, so its verdict is the class's verdict, byte for byte — and the class is always in
 *   this list whenever the property is, because the property is reachable only through it.
 *   Keeping both would print one rule twice and double the work a report looks like.
 *
 * `KoVisibilityModifierProvider` is required as well as [KoKDocProvider], which is what keeps
 * imports, package directives, annotations, enum constants and init blocks out: none of them
 * carries a visibility, and none of them is a declaration the rule is about.
 */
fun publicDeclarationsOf(file: KoFileDeclaration): List<KoBaseDeclaration> = file
    .declarations(includeNested = true, includeLocal = false)
    .filterNot { (it as? KoIsConstructorDefinedProvider)?.isConstructorDefined == true }
    .filterNot { (it as? KoOverrideModifierProvider)?.hasOverrideModifier == true }
    .filter { it is KoKDocProvider && isReachablePublic(it) }

/** Whether [declaration] shows how it is used. Its KDoc is read as text, heading and all. */
fun showsExample(declaration: KoBaseDeclaration): Boolean =
    kDocOf(declaration)?.contains(EXAMPLE_HEADING) == true

/**
 * Whether [declaration]'s KDoc keeps every block tag at its end, with nothing but more tags
 * after the first one.
 *
 * `null` (no KDoc at all) is not this rule's business — [KDOC_EXAMPLE_RULE] is what requires a
 * KDoc to exist, so a missing one is reported there instead of here.
 */
fun keepsBlockTagsLast(declaration: KoBaseDeclaration): Boolean =
    kDocOf(declaration)?.let { hasNothingAfterItsBlockTags(it) } != false

/**
 * Whether [kDocText] — the whole `/** ... */` block, delimiters included — never resumes prose
 * or a heading once its first block tag line has started.
 *
 * A KDoc is read as the paragraphs a blank ` *` line separates -- the same unit a person edits by
 * hand: a paragraph starting with `@` is a tag paragraph, and once one has been seen, every later
 * paragraph must also start with `@`. A fenced code block's lines never start a new paragraph of
 * their own, so a `@` or `#` written as an example inside one counts for nothing.
 */
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

/**
 * The KDoc of [declaration], asked of Konsist first and read off the file when Konsist has none.
 *
 * The fallback is not defensiveness, it is a measured hole. Konsist 0.17.3 parses with a Kotlin
 * PSI that predates context parameters, and a declaration written
 * `context(scope: LayoutScope) public fun ...` comes back with its PSI element starting at
 * `public` — the context clause and the KDoc above it are both outside it, so `kDoc` is `null`
 * however well documented the declaration is. Nineteen of katachi's layout vocabulary functions
 * are written that way. Dropping them from the rule would have been the cheaper fix and the
 * wrong one: they are exactly the declarations a reader meets first.
 *
 * Konsist stays the primary reading because it answers one case the text cannot. A `val` in a
 * primary constructor is documented by its class's KDoc, and Konsist returns that KDoc for it;
 * reading the line above such a property would find the constructor's opening parenthesis and
 * report every property of every documented data class.
 */
private fun kDocOf(declaration: KoBaseDeclaration): String? =
    (declaration as? KoKDocProvider)?.kDoc?.text ?: kDocAbove(declaration)

/**
 * The KDoc block written above [declaration] in its own file, or `null` when there is none.
 *
 * Blank lines, annotations, line comments and a context clause may sit between a KDoc and what
 * it documents, so they are stepped over. Anything else means the block above belongs to some
 * other declaration, and this one has no KDoc.
 */
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

/** Whether [trimmed] is a line that may sit between a KDoc and the declaration it documents. */
private fun isBetweenKDocAndDeclaration(trimmed: String): Boolean =
    trimmed.isEmpty() || trimmed.startsWith("@") || trimmed.startsWith("//") || trimmed.startsWith("context(")

/**
 * The 1-based line [declaration] starts on, read the way katachi reads it out of Konsist.
 *
 * `location` is `"$path:$line:$column"`, and the path is stripped as a prefix rather than split
 * on `:` because a Windows path carries one of its own at the drive letter.
 */
private fun lineOf(declaration: KoBaseDeclaration): Int? {
    val path = (declaration as? KoPathProvider)?.path ?: return null
    val location = (declaration as? KoLocationProvider)?.let { runCatching { it.location }.getOrNull() } ?: return null
    val rest = location.removePrefix("$path:")
    if (rest == location) return null
    return rest.substringBefore(':').toIntOrNull()
}

/**
 * Whether [declaration] is public and every type it sits inside is too.
 *
 * The walk stops at the file, which is the one containing declaration carrying no visibility of
 * its own — Konsist's `KoFileDeclaration` is not a `KoContainingDeclarationProvider`, so a top
 * level declaration answers here on its first step.
 */
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
