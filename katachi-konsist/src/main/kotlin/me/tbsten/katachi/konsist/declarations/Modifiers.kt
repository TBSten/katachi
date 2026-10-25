package me.tbsten.katachi.konsist.declarations

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.konsist.declarations.internal.ModifierSet
import me.tbsten.katachi.konsist.declarations.internal.VocabularyWord
import me.tbsten.katachi.konsist.declarations.internal.renderModifierAlternatives

/**
 * A modifier or annotations to pass to `allow(...)` in front of the kind: `public`,
 * `annotated("Serializable")`, `internal or private`, `suspend.optional()`.
 *
 * It holds one or more alternatives, each a modifier or a set of annotations; `or` puts the
 * alternatives of both sides together. A declaration line matches a declaration whose modifiers
 * are exactly the ones written, as in its Kotlin source: a modifier the line does not write may
 * not be on the declaration, except `public` and `final`, which Kotlin writes for none. A modifier
 * that may be there or not is written with [optional]. An annotation written has to be there, and
 * one not written is not asked about. `allow(..., anythingElse)` and `not.allow(...)` keep the
 * looser reading: what they write has to be there, and anything else may be too.
 *
 * Nothing is written until it is passed to `allow(...)` -- a value on its own is not a line, and
 * is refused when the block ends ([KtFileContentLineProblem.DanglingModifiers]). When the block
 * reads `fileName` or `captureValue`, the run made while the definition is read does not refuse it
 * (a value kept in a variable and used only under `if (fileName ...)` is unused for the stand-in);
 * the run for each file does.
 *
 * ## Example 1: modifiers in front of a kind, the way Kotlin writes them
 * ```kt
 * ktFileContent {
 *     `package`("com.example.**")
 *     `import`("kotlinx.serialization.**")
 *
 *     allow(public, data, `class`, fileName) { allow(`val`, "*") }  // public data class, nothing more, with its vals
 *     allow(suspend.optional(), `fun`, "execute")                  // fun execute or suspend fun execute
 *     allow((internal or private).optional(), data, `object`, "*")
 *     allow(annotated("Serializable"), `object` or `class`, "*Key")
 *     allow(private, anythingElse)
 * }
 * ```
 */
@ExperimentalKatachiApi
public class Modifiers internal constructor(
    internal val alternatives: List<ModifierSet>,
    internal val word: VocabularyWord,
    /** Made by [optional]: the declaration may carry one of the alternatives, or none. */
    internal val isOptional: Boolean = false,
) {
    /**
     * Either these modifiers or [other]'s.
     *
     * @throws KatachiKtFileContentLineException when [other] belongs to another block, or when
     *   either side is `.optional()`: put `.optional()` on the whole `or`, `(internal or private).optional()`.
     */
    public infix fun or(other: Modifiers): Modifiers {
        if (isOptional || other.isOptional) {
            throw KatachiKtFileContentLineException(
                problem = KtFileContentLineProblem.OptionalInsideOr,
                line = "$this or $other",
                declaredAt = DeclarationSite.capture(),
            )
        }
        val joined = (alternatives + other.alternatives).distinct()
        return Modifiers(joined, word.or(other.word, "$this or $other") { renderModifierAlternatives(joined) })
    }

    /**
     * These modifiers may be on the declaration, or not: `suspend.optional()` matches both
     * `fun execute` and `suspend fun execute`, and `(internal or private).optional()` no visibility,
     * `internal` or `private`. It is about one modifier of a line; it does not make the line's
     * declaration one that may be missing, which is what `allow(...)` in place of `required(...)` says.
     *
     * @throws KatachiKtFileContentLineException when these are annotations, which are not asked
     *   about unless written, or are `.optional()` already.
     */
    public fun optional(): Modifiers {
        val line = "$this.optional()"
        val problem = when {
            isOptional -> KtFileContentLineProblem.OptionalTwice
            alternatives.any { it.annotations.isNotEmpty() } -> KtFileContentLineProblem.OptionalAnnotation
            else -> null
        }
        if (problem != null) throw KatachiKtFileContentLineException(problem, line, DeclarationSite.capture())
        return Modifiers(alternatives, word.derive(line), isOptional = true)
    }

    /** `public data`, `(internal or private)`, `suspend.optional()`. */
    override fun toString(): String {
        val base = renderModifierAlternatives(alternatives)
        return if (isOptional) "$base.optional()" else base
    }
}
