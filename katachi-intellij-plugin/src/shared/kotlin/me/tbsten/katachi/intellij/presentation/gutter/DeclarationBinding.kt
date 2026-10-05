package me.tbsten.katachi.intellij.presentation.gutter

import me.tbsten.katachi.intellij.model.DeclarationModel
import kotlin.math.abs

/** A declaration of the file and what katachi said about it. */
internal data class BoundDeclaration(val source: SourceDeclaration, val explanation: BoundExplanation)

/**
 * Binds the top-level declarations the PSI has now to the ones katachi explained when it wrote the definition
 * (design 2.4), by kind and name, so that lines added above a declaration do not move its ◆ to another one.
 *
 * - Same number of declarations as explanations of that kind and name (one each, or overloaded functions): the n-th
 *   in line order takes the n-th.
 * - Fewer declarations than explanations (an overload was removed): each takes the explanation whose line is nearest,
 *   the earlier line on a tie.
 * - More declarations than explanations (an overload was added): each explanation goes to the nearest declaration
 *   not taken yet, the earlier line on a tie, and the others are [BoundExplanation.NotComputed]. The added one
 *   cannot be told from the old ones, and a rule that is bound by signature or visibility would be wrong to say
 *   "allowed" of it.
 * - None: [BoundExplanation.NotComputed]. An explanation no declaration takes (removed, renamed) is dropped.
 *
 * ```kotlin
 * val bound = DeclarationBinding.bind(sourceDeclarations, explanation.declarations)
 * bound.first().explanation // Explained(DeclarationModel(Class, "HealthService", 7, Allowed, ...))
 * ```
 */
internal object DeclarationBinding {
    fun bind(declarations: List<SourceDeclaration>, explanations: List<DeclarationModel>): List<BoundDeclaration> {
        val explained = explanations.groupBy { it.kind to it.name }.mapValues { (_, group) -> group.sortedBy { it.line } }
        // By index: two overloads on one line are equal as values.
        val answers = arrayOfNulls<BoundExplanation>(declarations.size)
        for ((key, indices) in declarations.indices.groupBy { declarations[it].kind to declarations[it].name }) {
            val candidates = explained[key].orEmpty()
            val inLineOrder = indices.sortedBy { declarations[it].line }
            if (candidates.isNotEmpty() && candidates.size == inLineOrder.size) {
                // As many as katachi saw: the order of overloads survives lines shifting, the distance between them does not.
                inLineOrder.zip(candidates).forEach { (index, model) -> answers[index] = BoundExplanation.Explained(model) }
            } else if (candidates.size < inLineOrder.size) {
                assignNearest(declarations, inLineOrder, candidates, answers)
            } else {
                inLineOrder.forEach { index -> answers[index] = nearest(declarations[index], candidates) }
            }
        }
        return declarations.mapIndexed { index, source -> BoundDeclaration(source, answers[index] ?: BoundExplanation.NotComputed) }
    }

    /** Each explanation, in line order, takes the nearest declaration not taken yet; the others stay unanswered. */
    private fun assignNearest(
        declarations: List<SourceDeclaration>,
        indices: List<Int>,
        candidates: List<DeclarationModel>,
        answers: Array<BoundExplanation?>,
    ) {
        val free = indices.toMutableList()
        for (candidate in candidates) {
            // free is in line order, so minBy keeps the earlier line on a tie.
            val index = free.minBy { abs(declarations[it].line - candidate.line) }
            free.remove(index)
            answers[index] = BoundExplanation.Explained(candidate)
        }
    }

    private fun nearest(source: SourceDeclaration, candidates: List<DeclarationModel>): BoundExplanation =
        // candidates are in line order, so minBy keeps the earlier line on a tie.
        candidates.minByOrNull { abs(it.line - source.line) }?.let(BoundExplanation::Explained) ?: BoundExplanation.NotComputed
}
