package me.tbsten.katachi.intellij.presentation.gutter

import me.tbsten.katachi.intellij.model.DeclarationModel
import kotlin.math.abs

/** A declaration of the file and what katachi said about it. */
internal data class BoundDeclaration(val source: SourceDeclaration, val explanation: BoundExplanation)

/**
 * Binds the top-level declarations the PSI has now to the ones katachi explained when it wrote the definition
 * (design 2.4), by kind and name, so that lines added above a declaration do not move its ◆ to another one.
 *
 * - One explanation of that kind and name: that one, wherever it is.
 * - Several (overloaded functions): when the file has as many of them as katachi saw, the n-th in line order takes
 *   the n-th; otherwise each takes the one whose line is nearest, the earlier line on a tie.
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
            } else {
                inLineOrder.forEach { index -> answers[index] = nearest(declarations[index], candidates) }
            }
        }
        return declarations.mapIndexed { index, source -> BoundDeclaration(source, answers[index] ?: BoundExplanation.NotComputed) }
    }

    private fun nearest(source: SourceDeclaration, candidates: List<DeclarationModel>): BoundExplanation =
        // candidates are in line order, so minBy keeps the earlier line on a tie.
        candidates.minByOrNull { abs(it.line - source.line) }?.let(BoundExplanation::Explained) ?: BoundExplanation.NotComputed
}
