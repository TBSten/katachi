package me.tbsten.katachi.konsist.declarations

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.konsist.declarations.internal.DeclarationListBuilder
import me.tbsten.katachi.konsist.declarations.internal.ModifierSet
import me.tbsten.katachi.konsist.declarations.internal.ModifierTrace
import me.tbsten.katachi.konsist.declarations.internal.Shape
import me.tbsten.katachi.konsist.declarations.internal.renderModifierAlternatives

/**
 * The modifiers and annotations written in front of a kind so far: `public.data`,
 * `annotated("Serializable")`, `(internal or private)`.
 *
 * It holds one or more alternatives, each a set of modifiers and annotations that all have to be
 * on a declaration. A word chained after it adds to every alternative; `or` puts the alternatives
 * of both sides together. Nothing is written until a kind is named and called with a name, or
 * until [anythingElse] -- a value on its own is not a line, and is refused when the block ends
 * ([KtFileContentLineProblem.DanglingModifiers]). Only modifiers that stop before any kind are
 * refused; a line that chains a kind but passes no name is not caught yet.
 *
 * ## Example 1: modifiers in front of a kind, the way Kotlin writes them
 * ```kt
 * ktFileContent {
 *     public.data.`class`(fileName)
 *     (internal or private).data.`object`("*")
 *     annotated("Serializable")(`object` or `class`)(fileName)
 *     const(`val` or `var`)("*")
 *     private.anythingElse()
 * }
 * ```
 */
@ExperimentalKatachiApi
public class Modifiers internal constructor(
    internal val alternatives: List<ModifierSet>,
    internal val prohibited: Boolean,
    internal val sink: DeclarationListBuilder,
    private val trace: ModifierTrace,
) : DeclarationVocabulary by VocabularyImpl(alternatives, prohibited, sink, trace) {
    /**
     * Either these modifiers or [other]'s.
     *
     * ## Example 1: internal or private data objects
     * ```kt
     * (internal or private).data.`object`("*")
     * ```
     *
     * @throws KatachiKtFileContentLineException when only one side starts with `not.`.
     */
    public infix fun or(other: Modifiers): Modifiers {
        checkJoinable(other.prohibited, other.sink, "$this or $other")
        trace.use()
        other.trace.use()
        val joined = (alternatives + other.alternatives).distinct()
        val joinedTrace = ModifierTrace(joined, prohibited, trace.declaredAt)
        return Modifiers(joined, prohibited, sink, sink.session.modifiers.track(joinedTrace))
    }

    /**
     * [kind] with these modifiers in front of each of its kinds, keeping only the kinds they can be
     * on: `` data(`class` or `interface`) `` is a data class.
     *
     * ## Example 1: a serializable object or class named after the file
     * ```kt
     * annotated("Serializable")(`object` or `class`)(fileName)
     * ```
     */
    public operator fun invoke(kind: TypeKind): TypeKind {
        checkSameBlock(kind.sink, "$this($kind)")
        trace.use()
        return TypeKind(combined(kind.shapes), prohibited || kind.prohibited, sink)
    }

    /**
     * [kind] with these modifiers in front, keeping only the kinds they can be on:
     * `` const(`val` or `var`) `` is a const val.
     *
     * ## Example 1: constants, whichever keyword they were written with
     * ```kt
     * const(`val` or `var`)("*")
     * ```
     */
    public operator fun invoke(kind: PropertyKind): PropertyKind {
        checkSameBlock(kind.sink, "$this($kind)")
        trace.use()
        return PropertyKind(combined(kind.shapes), prohibited || kind.prohibited, sink)
    }

    /**
     * Allows any declaration these modifiers are on, of whatever kind and name, that no other line
     * matches.
     *
     * ## Example 1: any private member
     * ```kt
     * `class`(fileName) { private.anythingElse() }
     * ```
     *
     * @throws KatachiKtFileContentLineException when written after `not.`.
     */
    public fun anythingElse() {
        trace.use()
        sink.addAnythingElse(alternatives, prohibited)
    }

    private fun combined(shapes: List<Shape>): List<Shape> =
        alternatives.flatMap { set -> shapes.map { Shape(set.union(it.modifiers), it.kind) } }.distinct()

    private fun checkJoinable(otherProhibited: Boolean, otherSink: DeclarationListBuilder, line: String) {
        checkSameBlock(otherSink, line)
        if (otherProhibited == prohibited) return
        throw KatachiKtFileContentLineException(KtFileContentLineProblem.NotInsideOr, line, DeclarationSite.capture())
    }

    private fun checkSameBlock(otherSink: DeclarationListBuilder, line: String) {
        if (otherSink === sink) return
        throw KatachiKtFileContentLineException(KtFileContentLineProblem.OutsideItsBlock, line, DeclarationSite.capture())
    }

    /** `public data`, `not (internal or private)`. */
    override fun toString(): String = (if (prohibited) "not " else "") + renderModifierAlternatives(alternatives)
}
