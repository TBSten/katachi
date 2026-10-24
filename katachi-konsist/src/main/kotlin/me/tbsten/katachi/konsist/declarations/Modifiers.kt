package me.tbsten.katachi.konsist.declarations

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.konsist.declarations.internal.ModifierSet
import me.tbsten.katachi.konsist.declarations.internal.VocabularyWord
import me.tbsten.katachi.konsist.declarations.internal.renderModifierAlternatives

/**
 * A modifier or annotations to pass to `allow(...)` in front of the kind: `public`,
 * `annotated("Serializable")`, `internal or private`.
 *
 * It holds one or more alternatives, each a set of modifiers and annotations that all have to be
 * on a declaration; `or` puts the alternatives of both sides together. Nothing is written until it
 * is passed to `allow(...)` -- a value on its own is not a line, and is refused when the block ends
 * ([KtFileContentLineProblem.DanglingModifiers]). When the block reads `fileName` or
 * `captureValue`, the run made while the definition is read does not refuse it (a value kept in a
 * variable and used only under `if (fileName ...)` is unused for the stand-in); the run for each
 * file does.
 *
 * ## Example 1: modifiers in front of a kind, the way Kotlin writes them
 * ```kt
 * ktFileContent {
 *     allow(public, data, `class`, fileName)
 *     allow(internal or private, data, `object`, "*")
 *     allow(annotated("Serializable"), `object` or `class`, fileName)
 *     allow(private, anythingElse)
 * }
 * ```
 */
@ExperimentalKatachiApi
public class Modifiers internal constructor(
    internal val alternatives: List<ModifierSet>,
    internal val word: VocabularyWord,
) {
    /**
     * Either these modifiers or [other]'s.
     *
     * @throws KatachiKtFileContentLineException when [other] belongs to another block.
     */
    public infix fun or(other: Modifiers): Modifiers {
        val joined = (alternatives + other.alternatives).distinct()
        return Modifiers(joined, word.or(other.word, "$this or $other") { renderModifierAlternatives(joined) })
    }

    /** `public data`, `(internal or private)`. */
    override fun toString(): String = renderModifierAlternatives(alternatives)
}
