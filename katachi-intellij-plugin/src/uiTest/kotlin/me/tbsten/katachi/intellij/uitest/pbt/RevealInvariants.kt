package me.tbsten.katachi.intellij.uitest.pbt

import io.kotest.property.Arb
import io.kotest.property.arbitrary.choose
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.map
import me.tbsten.katachi.intellij.model.KatachiModule
import me.tbsten.katachi.intellij.model.TemplateId
import me.tbsten.katachi.intellij.presentation.BodyUi
import me.tbsten.katachi.intellij.presentation.KatachiIntent
import me.tbsten.katachi.intellij.presentation.KatachiScreenState
import me.tbsten.katachi.intellij.presentation.KatachiUiState
import me.tbsten.katachi.intellij.presentation.ListItemUi

/*
 * [View template] of an editor notification in the property tests (C2): what it targets, and what
 * must hold after it and after every other step.
 */

/**
 * [stepArb] with [View template] mixed in. A separate generator, so that the sequences of the
 * other property tests stay the ones their fixed seed has always played.
 */
internal val stepWithRevealArb: Arb<List<Op>> = Arb.choose(
    8 to stepArb,
    1 to Arb.int(0..15).map { listOf(Op.Reveal(it)) },
)

/** [pick] among the list's templates, shown or not, and one past them a template the list does not have. */
internal fun revealTargetOf(pick: Int, state: KatachiScreenState, module: KatachiModule): TemplateId {
    val ids = state.rows.map { it.id }
    val index = pick % (ids.size + 1)
    return ids.getOrNull(index) ?: TemplateId(module.id, "gone.NotInTheList")
}

/**
 * At most one row is highlighted, the one the state names with its request number. Right after
 * [View template] of a template in the list, its row is on the list (no search hides it, its
 * module band is open) and highlighted.
 */
internal fun revealViolationsOf(state: KatachiScreenState, intents: List<KatachiIntent>, ui: KatachiUiState): List<String> = buildList {
    val list = (ui.body as? BodyUi.Listing)?.list ?: return@buildList
    val rows = list.items.mapNotNull { (it as? ListItemUi.Row)?.row }
    val highlighted = rows.filter { it.highlight != null }
    val expected = state.view.highlight
    if (highlighted.size > 1) add("several rows are highlighted: ${highlighted.map { it.id.template }}")
    highlighted.singleOrNull()?.let { row ->
        if (expected == null || row.id != expected.templateId || row.highlight != expected.sequence) {
            add("${row.id.template} is highlighted (${row.highlight}), the state names $expected")
        }
    }
    val reveal = intents.lastOrNull() as? KatachiIntent.RevealTemplate ?: return@buildList
    if (state.rows.none { it.id == reveal.templateId }) return@buildList
    if (highlighted.singleOrNull()?.id != reveal.templateId) {
        add("after [View template] of ${reveal.templateId.template} the list shows ${highlighted.map { it.id.template }} highlighted")
    }
    if (list.emptySearch != null) add("after [View template] of ${reveal.templateId.template} the search hides every row")
}
