package me.tbsten.katachi.konsist.declarations

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.KatachiDeclarationException
import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.KatachiGlobSyntaxException
import me.tbsten.katachi.konsist.declarations.internal.DeclarationKindName
import me.tbsten.katachi.konsist.declarations.internal.KtFileContentPatternRefusal
import me.tbsten.katachi.konsist.declarations.internal.ModifierName
import me.tbsten.katachi.konsist.declarations.internal.applicableKinds

/**
 * A line of `ktFileContent { }` whose modifiers no declaration can have, raised while the
 * definition is read.
 *
 * The block is evaluated once with stand-in values (`fileName` is `Sample`) as soon as
 * `ktFileContent { }` is called, so a line like `` data.`interface`(fileName) `` fails there and
 * then, pointing at the line, rather than matching nothing in every file. The same check runs for
 * every file during the check, where a line only one file reaches (behind an `if`) is caught.
 *
 * ## Example 1: catch a modifier that the kind cannot carry
 * ```kt
 * val error = shouldThrow<KatachiKtFileContentModifierException> {
 *     architecture {
 *         "UiState".role {
 *             layout { "*UiState".ktFile() }
 *             ktFileContent { data.`interface`(fileName) }
 *         }
 *     }
 * }
 * error.line shouldBe "data interface Sample"
 * error.problem shouldBe KtFileContentModifierProblem.NotApplicable
 * ```
 */
@ExperimentalKatachiApi
public class KatachiKtFileContentModifierException internal constructor(
    /**
     * The line as katachi reads it, with the stand-in values filled in: `data interface Sample`.
     *
     * ## Example 1: read back the line that was refused
     * ```kt
     * error.line shouldBe "public internal class Sample"
     * ```
     */
    public val line: String,
    /**
     * What is wrong with the modifiers.
     *
     * ## Example 1: tell two modifiers that exclude each other from one the kind cannot carry
     * ```kt
     * error.problem shouldBe KtFileContentModifierProblem.Conflicting
     * ```
     */
    public val problem: KtFileContentModifierProblem,
    /**
     * The modifiers the problem is about, in the order Kotlin writes them: both of a conflicting
     * pair, or the one that cannot be where it is.
     *
     * ## Example 1: read which modifiers conflict
     * ```kt
     * error.modifiers shouldBe listOf("public", "internal")
     * ```
     */
    public val modifiers: List<String>,
    /**
     * The kind the modifier cannot be on, or `null` when the problem is not about a kind.
     *
     * ## Example 1: read which kind could not carry the modifier
     * ```kt
     * error.kind shouldBe "interface"
     * ```
     */
    public val kind: String?,
    /**
     * Where the line was written.
     *
     * ## Example 1: jump to the line to fix
     * ```kt
     * error.declaredAt.fileName shouldBe "UiStateRole.kt"
     * ```
     */
    public val declaredAt: DeclarationSite,
) : KatachiDeclarationException(message = modifierMessage(line, problem, modifiers, kind, declaredAt))

/**
 * A pattern of `ktFileContent { }` that katachi's glob cannot read, raised while the definition
 * is read.
 *
 * Every name, path and type is a pattern with `*` and `**` only, the same glob `layout { }` reads:
 * `?`, `{a,b}` and `[abc]` are refused rather than read as literals. In a type, `?` says nullable
 * and is allowed.
 *
 * ## Example 1: catch an import pattern with an empty level
 * ```kt
 * val error = shouldThrow<KatachiKtFileContentPatternException> {
 *     architecture {
 *         "Screen".role {
 *             layout { "*Screen".ktFile() }
 *             ktFileContent { `import`("androidx..compose") }
 *         }
 *     }
 * }
 * error.what shouldBe KtFileContentPatternKind.Import
 * ```
 */
@ExperimentalKatachiApi
public class KatachiKtFileContentPatternException internal constructor(
    /**
     * The pattern as it was written.
     *
     * ## Example 1: read back the pattern that could not be read
     * ```kt
     * error.pattern shouldBe "androidx..compose"
     * ```
     */
    public val pattern: String,
    /**
     * Which part of the line the pattern was written for.
     *
     * ## Example 1: tell an import pattern from a name
     * ```kt
     * error.what shouldBe KtFileContentPatternKind.Import
     * ```
     */
    public val what: KtFileContentPatternKind,
    /**
     * Where the pattern was written.
     *
     * ## Example 1: jump to the pattern to fix
     * ```kt
     * error.declaredAt.lineNumber
     * ```
     */
    public val declaredAt: DeclarationSite,
    cause: KatachiGlobSyntaxException?,
    /** Why the pattern is refused when the glob reads it but katachi would read it wrongly. */
    problem: KtFileContentPatternRefusal? = null,
) : KatachiDeclarationException(message = patternMessage(pattern, what, declaredAt, cause, problem), cause = cause)

/**
 * A line of `ktFileContent { }` written in a way that cannot mean anything, raised while the
 * definition is read: `.except(...)` on an allowed line, two `{ }`, a `typealias` in a class, and
 * the rest of [KtFileContentLineProblem].
 *
 * ## Example 1: catch `.except(...)` on a line that does not start with `not.`
 * ```kt
 * val error = shouldThrow<KatachiKtFileContentLineException> {
 *     architecture {
 *         "Screen".role {
 *             layout { "*Screen".ktFile() }
 *             ktFileContent { `import`("kotlin.**").except("kotlin.io.**") }
 *         }
 *     }
 * }
 * error.problem shouldBe KtFileContentLineProblem.ExceptWithoutNot
 * ```
 */
@ExperimentalKatachiApi
public class KatachiKtFileContentLineException internal constructor(
    /**
     * What is wrong with the line.
     *
     * ## Example 1: tell which mistake the line made
     * ```kt
     * error.problem shouldBe KtFileContentLineProblem.BodyWrittenTwice
     * ```
     */
    public val problem: KtFileContentLineProblem,
    /**
     * The line as katachi reads it, with the stand-in values filled in: `import kotlin.**`.
     *
     * ## Example 1: read back the line
     * ```kt
     * error.line shouldBe "import kotlin.**"
     * ```
     */
    public val line: String,
    /**
     * Where the line was written.
     *
     * ## Example 1: jump to the line to fix
     * ```kt
     * error.declaredAt.fileName shouldBe "ScreenRole.kt"
     * ```
     */
    public val declaredAt: DeclarationSite,
) : KatachiDeclarationException(message = lineMessage(problem, line, declaredAt))

private fun modifierMessage(
    line: String,
    problem: KtFileContentModifierProblem,
    modifiers: List<String>,
    kind: String?,
    declaredAt: DeclarationSite,
): String {
    val named = modifiers.joinToString(" and ")
    val (why, how) = when (problem) {
        KtFileContentModifierProblem.Conflicting ->
            "$named cannot be on one declaration together." to
                "Write (${modifiers.joinToString(" or ")}) to accept either, or keep only one of them."

        KtFileContentModifierProblem.OnlyInClassBody -> if (kind == null) {
            "$named is only valid on a member of a class, and this line is written directly in the file." to
                "Move the line into the { } of a class line."
        } else {
            "$named on a $kind is only valid inside a class, and this line is written directly in the file." to
                "Move the line into the { } of a class line, or remove $named."
        }

        KtFileContentModifierProblem.NotApplicable -> {
            val kinds = applicableKindsOf(modifiers)
            "$named can be put on $kinds only, so no declaration has this shape." to
                "Remove $named, or use $kinds."
        }
    }
    return listOf("Line \"$line\" in ktFileContent { } at $declaredAt cannot match anything.", why, how)
        .joinToString("\n")
}

/** `class or object`: the kinds the first of [modifiers] can be put on, in the order Kotlin's keywords are listed. */
private fun applicableKindsOf(modifiers: List<String>): String {
    val modifier = ModifierName.entries.firstOrNull { it.keyword == modifiers.firstOrNull() } ?: return "nothing"
    val kinds = DeclarationKindName.entries.filter { it in modifier.applicableKinds() }.map { it.keyword }
    return when (kinds.size) {
        0 -> "nothing"
        1 -> kinds.single()
        else -> kinds.dropLast(1).joinToString(", ") + " or " + kinds.last()
    }
}

private fun patternMessage(
    pattern: String,
    what: KtFileContentPatternKind,
    declaredAt: DeclarationSite,
    cause: KatachiGlobSyntaxException?,
    problem: KtFileContentPatternRefusal?,
): String = listOfNotNull(
    "The ${what.label} pattern \"$pattern\" in ktFileContent { } at $declaredAt cannot be read.",
    problem?.why ?: cause?.message?.lineSequence()?.firstOrNull()?.takeIf { it.isNotBlank() },
    problem?.how ?: "Write the pattern with * and ** only; escape a literal character with \\.",
).joinToString("\n")

private fun lineMessage(problem: KtFileContentLineProblem, line: String, at: DeclarationSite): String {
    val (what, why, how) = when (problem) {
        KtFileContentLineProblem.ExceptWithoutNot -> Triple(
            ".except(...) on line \"$line\" at $at has no effect.",
            "except narrows a prohibited line; an allowed line already allows only what it matches.",
            "Start the line with not., or remove .except(...).",
        )

        KtFileContentLineProblem.ExceptWithoutNames -> Triple(
            ".except() on line \"$line\" at $at names no pattern.",
            "except lists what a prohibited line lets through, and an empty list lets nothing through.",
            "Pass at least one pattern, or remove .except().",
        )

        KtFileContentLineProblem.OptionalOnProhibited -> Triple(
            ".optional() on line \"$line\" at $at has no effect.",
            "A prohibited line is never required, so there is nothing to make optional.",
            "Remove .optional(), or remove not. if the line is meant to allow something.",
        )

        KtFileContentLineProblem.BodyWrittenTwice -> Triple(
            "Line \"$line\" at $at is given a { } twice.",
            "A class line has one body: the { } after the name and the { } after .superclass(...) would both list its members.",
            "Write the members in one of the two { }.",
        )

        KtFileContentLineProblem.BodyOnProhibited -> Triple(
            "Line \"$line\" at $at prohibits a declaration and also lists its members.",
            "A declaration a not. line matches is reported whatever its members are, so the { } would never be read.",
            "Remove the { }, or remove not. if the line is meant to allow the declaration.",
        )

        KtFileContentLineProblem.ParametersWrittenTwice -> Triple(
            "Line \"$line\" at $at writes its parameters twice.",
            "A function has one parameter list: the types in (...) after the name, or anyArgs().",
            "Keep one of the two.",
        )

        KtFileContentLineProblem.ReturnTypeWrittenTwice -> Triple(
            "Line \"$line\" at $at writes .returns(...) twice.",
            "A function has one return type.",
            "Keep one .returns(...).",
        )

        KtFileContentLineProblem.SuperclassWithoutNames -> Triple(
            ".superclass() on line \"$line\" at $at names no supertype.",
            "superclass lists the supertypes a declaration must have, and an empty list asks for nothing.",
            "Pass at least one name, or remove .superclass().",
        )

        KtFileContentLineProblem.NotInsideOr -> Triple(
            "\"$line\" at $at joins a prohibited part and an allowed part with or.",
            "A line either allows or prohibits: not. applies to the whole line, so it cannot sit on one side of or.",
            "Put not. in front of every part, or write the two parts as two lines.",
        )

        KtFileContentLineProblem.TypeAliasInClassBody -> Triple(
            "Line \"$line\" at $at puts a typealias inside the { } of a class.",
            "Kotlin declares a typealias only directly in a file, so the line could never match a member.",
            "Move the line out of the { }, directly into ktFileContent { }.",
        )

        KtFileContentLineProblem.AnnotatedWithoutNames -> Triple(
            "$line at $at names no annotation.",
            "annotated lists the annotations a declaration must carry, and an empty list asks for nothing.",
            "Pass at least one name, such as annotated(\"Composable\"), or remove it.",
        )

        KtFileContentLineProblem.AnythingElseAfterNot -> Triple(
            "Line \"$line\" at $at prohibits anythingElse().",
            "anythingElse() allows what no other line matches; after not. it names nothing in particular to prohibit.",
            "Write what to prohibit as lines of their own, such as not.private.`fun`(\"*\"), or remove not.",
        )

        KtFileContentLineProblem.OutsideItsBlock -> Triple(
            "Line \"$line\" at $at is written with a value that belongs to another block.",
            "A line goes to the block whose vocabulary built it, so a value kept in a variable would put it " +
                "somewhere other than where it is written, or into an evaluation that has already ended.",
            "Write the line with the vocabulary of the block it is in, such as private.anythingElse() directly " +
                "inside the { } of the class.",
        )
    }
    return listOf(what, why, how).joinToString("\n")
}
