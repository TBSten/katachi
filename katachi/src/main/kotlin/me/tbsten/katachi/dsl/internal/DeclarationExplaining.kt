package me.tbsten.katachi.dsl.internal

import me.tbsten.katachi.InternalKatachiApi
import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.FileConstraintSubject

/**
 * A file constraint that can say which of its lines allowed or rejected each top-level declaration of a file.
 *
 * Tools such as the IDE plugin show this next to each declaration; `ktFileContent { }`
 * implements it. An explanation must decide each declaration exactly as
 * [me.tbsten.katachi.dsl.FileConstraint.evaluate] judges it: a declaration whose verdict is not
 * allowed is one the check rejects, and a missing line is one the check reports missing.
 *
 * ## Example 1: explain the declarations of the files a subject covers
 * ```kt
 * val explaining = constraint as? DeclarationExplaining
 * val explained: List<FileDeclarationsExplanation>? = explaining?.explainDeclarations(subject)
 * explained?.forEach { file ->
 *     file.declarations.filter { it.verdict == DeclarationVerdict.Allowed }.map { it.name }
 * }
 * ```
 *
 * @see FileDeclarationsExplanation
 */
@InternalKatachiApi
public interface DeclarationExplaining {
    /**
     * One explanation per covered file the constraint reads, in the order of
     * [FileConstraintSubject.files].
     *
     * A file the constraint does not read (not a `.kt` file, for
     * `ktFileContent { }`) is left out. A file whose own rules could not be worked out is still
     * listed, with [FileDeclarationsExplanation.unexplainedReason]; what fails for every file at
     * once is thrown.
     */
    public fun explainDeclarations(subject: FileConstraintSubject): List<FileDeclarationsExplanation>
}

/**
 * How one file's top-level declarations were decided, and which required lines none of them met.
 *
 * ## Example 1: list what a file was not allowed to declare
 * ```kt
 * val file: FileDeclarationsExplanation = explaining.explainDeclarations(subject).first()
 * file.declarations.filterNot { it.verdict.isAllowed }.map { "${it.name} (line ${it.line})" }
 * ```
 *
 * @constructor Takes the file and what was decided in it.
 */
@InternalKatachiApi
public class FileDeclarationsExplanation(
    /** Project relative, `/` separated, as [FileConstraintSubject.files] names it. */
    public val file: String,
    /**
     * Whether the top-level declarations of this file were judged.
     *
     * `ktFileContent { }` judges them
     * even when it writes no declaration line (deny by default: then none is allowed), so this is
     * `false` only when the file could not be explained ([unexplainedReason]), with [declarations] empty.
     */
    public val declarationsChecked: Boolean,
    /** The top-level declarations of the file, in the order they are written. */
    public val declarations: List<DeclarationExplanation>,
    /** The required lines no declaration of the file met, in the order they were written. */
    public val missing: List<RuleExplanation>,
    /**
     * Why this file could not be explained -- the type of what was thrown and the first line of its
     * message -- or `null` when it was.
     *
     * When set, [declarations] and [missing] are empty.
     */
    public val unexplainedReason: String?,
) {
    override fun toString(): String = "FileDeclarationsExplanation($file, ${declarations.size} declarations)"
}

/**
 * How one top-level declaration was decided, and by which lines.
 *
 * ## Example 1: say which line allowed a declaration
 * ```kt
 * val declaration: DeclarationExplanation = file.declarations.first()
 * if (declaration.verdict == DeclarationVerdict.Allowed) println(declaration.rules.first().declaredAt)
 * ```
 *
 * @constructor Takes the declaration and what was decided about it.
 */
@InternalKatachiApi
public class DeclarationExplanation(
    /** `class`, `interface`, `object`, `fun`, `val`, `var` or `typealias`: the keyword it is declared with. */
    public val kind: String,
    /** Its name, without the receiver of an extension. */
    public val name: String,
    /** The line it starts at (its first annotation, if any), or `null` when it could not be placed. */
    public val line: Int?,
    /** Whether a line allowed it, and how. */
    public val verdict: DeclarationVerdict,
    /**
     * The lines that decided it; empty for [DeclarationVerdict.Unexpected].
     *
     * Every allowed line it matches for [DeclarationVerdict.Allowed], the matching
     * `allow(..., anythingElse)` lines, the prohibiting line, or the line whose name it matches
     * for [DeclarationVerdict.Mismatched].
     */
    public val rules: List<RuleExplanation>,
    /** What differs from the line, for [DeclarationVerdict.Mismatched]; `null` otherwise. */
    public val difference: String?,
) {
    override fun toString(): String = "DeclarationExplanation($kind $name, $verdict)"
}

/**
 * One line of a `ktFileContent { }` block, as the report prints it and where it was written.
 *
 * ## Example 1: point at the line that allowed a declaration
 * ```kt
 * val rule = RuleExplanation(text = "class HealthService", declaredAt = DeclarationSite("ServiceRole.kt", 80))
 * println("${rule.text} at ${rule.declaredAt}")
 * ```
 *
 * @constructor Takes the line as printed and where it was written.
 */
@InternalKatachiApi
public class RuleExplanation(
    /** The line as the report's `Allowed here:` prints it, with this file's values filled in. */
    public val text: String,
    /** Where the line was written: a helper's own line when a helper wrote it. */
    public val declaredAt: DeclarationSite,
) {
    override fun toString(): String = "RuleExplanation($text, $declaredAt)"
}

/**
 * How a top-level declaration was decided.
 *
 * ## Example 1: count the declarations a check would reject
 * ```kt
 * file.declarations.count { !it.verdict.isAllowed }
 * ```
 *
 * @property isAllowed whether the check lets the declaration be.
 */
@InternalKatachiApi
public enum class DeclarationVerdict(public val isAllowed: Boolean) {
    /** An allowed line matches it in full. */
    Allowed(isAllowed = true),

    /** No line matches it, and an `allow(..., anythingElse)` lets it be. */
    AllowedByAnythingElse(isAllowed = true),

    /** A `not.` line matches it. */
    Prohibited(isAllowed = false),

    /** An allowed line matches its name, and not the rest of it. */
    Mismatched(isAllowed = false),

    /** No line matches it, not even by name, and no `allow(..., anythingElse)` lets it be. */
    Unexpected(isAllowed = false),
}
