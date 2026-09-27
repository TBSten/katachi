package me.tbsten.katachi.intellij.uitest.pbt

import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.choose
import io.kotest.property.arbitrary.constant
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.map
import me.tbsten.katachi.intellij.model.ConflictChoice
import me.tbsten.katachi.intellij.presentation.BodyUi
import me.tbsten.katachi.intellij.presentation.FieldUi
import me.tbsten.katachi.intellij.presentation.FooterUi
import me.tbsten.katachi.intellij.presentation.KatachiIntent
import me.tbsten.katachi.intellij.presentation.KatachiUiState
import me.tbsten.katachi.intellij.presentation.LinkUi
import me.tbsten.katachi.intellij.presentation.ListItemUi
import me.tbsten.katachi.intellij.presentation.ListUi
import me.tbsten.katachi.intellij.presentation.RowBodyUi
import me.tbsten.katachi.intellij.presentation.RowLeadUi
import me.tbsten.katachi.intellij.presentation.RowMarker
import me.tbsten.katachi.intellij.presentation.TemplateRowUi
import me.tbsten.katachi.intellij.presentation.TitleAction

/**
 * One step of a property test: something the user does on the screen, or something that happens
 * around it (a sync, a definition change, how the next Gradle run ends).
 *
 * A user operation names its target by an index into what the screen offers at that moment
 * ([pick] modulo the count), so that every generated sequence only does what the screen allows,
 * and a shrunk sequence stays valid.
 */
internal sealed interface Op {
    // What the user does on the screen.
    data class Check(val pick: Int) : Op
    data class Fold(val pick: Int) : Op
    data class Type(val pick: Int, val value: Int) : Op
    data class Toggle(val pick: Int) : Op
    data class Choose(val pick: Int, val option: Int) : Op
    data class Relink(val pick: Int) : Op
    data class Multiline(val pick: Int) : Op
    data class Search(val query: Int) : Op
    data class OnExisting(val choice: Int) : Op
    data object Generate : Op
    /** Generate pressed twice before the screen updates (a double click, Ctrl+Enter held). */
    data object GenerateTwice : Op
    data class FooterButton(val pick: Int) : Op
    data object RevealReason : Op
    data class Banner(val pick: Int) : Op
    data class Menu(val pick: Int) : Op
    data class Details(val pick: Int) : Op
    data class ModuleBand(val pick: Int) : Op
    data object TitleButton : Op
    data class MessageButton(val pick: Int) : Op

    // What happens around the screen.
    /** The definition on disk becomes the catalog's world [world]; nothing reloads by itself. */
    data class Redefine(val world: Int) : Op
    data object Sync : Op
    data object DefinitionChanged : Op
    data class NextLoad(val outcome: RunOutcome) : Op
    data class NextRun(val outcome: RunOutcome) : Op
    data class ConflictAnswer(val choice: ConflictChoice, val waits: Boolean) : Op
    /** Every build and dialog that is waiting ends now. */
    data object Release : Op
}

/** How a scripted Gradle run ends. */
internal enum class RunOutcome { Normal, Fails, Waits }

/** Values typed into text fields: empty, blank, names, numbers, a line break, Japanese. */
internal val TYPED_VALUES = listOf("", "User", "Order", " ", "3", "abc", "x\ny", "日本語", "12345678901", "-1")

/** Queries typed into the search field. */
internal val QUERIES = listOf("", "r", "R1", "zzz", " ", "a/b", "タイトル", "NAME")

private val pick = Arb.int(0..15)

/** Weighted towards what builds up state (checking, typing, generating, letting builds end). */
internal val opArb: Arb<Op> = Arb.choose(
    8 to pick.map { Op.Check(it) },
    2 to pick.map { Op.Fold(it) },
    8 to Arb.bind(pick, Arb.int(TYPED_VALUES.indices)) { target, value -> Op.Type(target, value) },
    3 to pick.map { Op.Toggle(it) },
    3 to Arb.bind(pick, Arb.int(0..2)) { target, option -> Op.Choose(target, option) },
    1 to pick.map { Op.Relink(it) },
    2 to pick.map { Op.Multiline(it) },
    3 to Arb.int(QUERIES.indices).map { Op.Search(it) },
    2 to Arb.int(0..2).map { Op.OnExisting(it) },
    6 to Arb.constant(Op.Generate),
    2 to Arb.constant(Op.GenerateTwice),
    5 to pick.map { Op.FooterButton(it) },
    1 to Arb.constant(Op.RevealReason),
    2 to pick.map { Op.Banner(it) },
    1 to pick.map { Op.Menu(it) },
    1 to pick.map { Op.Details(it) },
    1 to pick.map { Op.ModuleBand(it) },
    3 to Arb.constant(Op.TitleButton),
    1 to pick.map { Op.MessageButton(it) },
    3 to Arb.int(0..3).map { Op.Redefine(it) },
    2 to Arb.constant(Op.Sync),
    1 to Arb.constant(Op.DefinitionChanged),
    2 to Arb.enum<RunOutcome>().map { Op.NextLoad(it) },
    3 to Arb.enum<RunOutcome>().map { Op.NextRun(it) },
    2 to Arb.int(0..5).map { Op.ConflictAnswer(ConflictChoice.entries[it % 3], waits = it >= 3) },
    4 to Arb.constant(Op.Release),
)

private fun <T> List<T>.pick(index: Int): T? = if (isEmpty()) null else this[index % size]

private fun ListUi.rows(): List<TemplateRowUi> = items.mapNotNull { (it as? ListItemUi.Row)?.row }

private fun ListUi.fields(): List<FieldUi> = rows().flatMap { (it.body as? RowBodyUi.Form)?.form?.fields.orEmpty() }

/**
 * The intents [op] sends through the screen showing [ui], as the Composables would: only targets
 * that are on screen and enabled. Empty when the screen offers nothing of the kind.
 */
internal fun intentsOf(op: Op, ui: KatachiUiState): List<KatachiIntent> {
    if (op == Op.TitleButton) {
        return listOf(if (ui.titleAction == TitleAction.Stop) KatachiIntent.CancelLoad else KatachiIntent.Reload)
    }
    val list = (ui.body as? BodyUi.Listing)?.list ?: return messageIntentsOf(op, ui.body)
    val footer = list.footer
    return when (op) {
        is Op.Check -> list.rows().filter { (it.lead as? RowLeadUi.Check)?.enabled == true }.pick(op.pick)
            ?.let { listOf(KatachiIntent.ToggleCheck(it.id)) }
        is Op.Fold -> list.rows().filter { it.isExpanded != null }.pick(op.pick)
            ?.let { listOf(KatachiIntent.SetExpanded(it.id, !it.isExpanded!!)) }
        is Op.Type -> list.fields().filterIsInstance<FieldUi.Text>().pick(op.pick)
            ?.let { listOf(KatachiIntent.Input(it.id, TYPED_VALUES[op.value])) }
        is Op.Toggle -> list.fields().filterIsInstance<FieldUi.Bool>().pick(op.pick)
            ?.let { listOf(KatachiIntent.Input(it.id, (!it.checked).toString())) }
        is Op.Choose -> list.fields().filterIsInstance<FieldUi.Choice>().pick(op.pick)
            ?.let { field -> field.options.pick(op.option)?.let { listOf(KatachiIntent.Input(field.id, it)) } }
        is Op.Relink -> list.fields().mapNotNull { linkOf(it) as? LinkUi.Unlinked }.pick(op.pick)?.let { listOf(it.relink) }
        is Op.Multiline -> list.fields().filterIsInstance<FieldUi.Text>().filter { it.isMultiline != null }.pick(op.pick)
            ?.let { listOf(KatachiIntent.ToggleMultiline(it.id)) }
        is Op.Search -> if (list.search.enabled) listOf(KatachiIntent.Search(QUERIES[op.query])) else null
        is Op.OnExisting -> (footer as? FooterUi.Form)?.onExistingOptions?.pick(op.choice)?.let { listOf(it.intent) }
        Op.Generate -> if (footer is FooterUi.Form && footer.generateEnabled) listOf(KatachiIntent.Generate) else null
        Op.GenerateTwice -> if (footer is FooterUi.Form && footer.generateEnabled) listOf(KatachiIntent.Generate, KatachiIntent.Generate) else null
        is Op.FooterButton -> when (footer) {
            is FooterUi.Generating -> listOf(footer.cancel.intent)
            is FooterUi.Result -> footer.actions.pick(op.pick)?.let { listOf(it.intent) }
            is FooterUi.Form -> null
        }
        Op.RevealReason -> (footer as? FooterUi.Form)?.reasonTarget?.let { listOf(KatachiIntent.RevealField(it)) }
        is Op.Banner -> (
            list.banners.flatMap { banner -> banner.actions.map { it.intent } + listOfNotNull(banner.dismiss) } +
                listOfNotNull(list.refresh?.cancel?.intent) + list.emptySearch?.actions?.map { it.intent }.orEmpty()
            ).pick(op.pick)?.let { listOf(it) }
        // The menu shows on a row with an open form, and on a row that cannot be checked (TemplateRow).
        is Op.Menu -> list.rows().filter { it.body is RowBodyUi.Form || it.marker == RowMarker.Blocked }
            .flatMap { row -> row.menu.map { it.intent } }.pick(op.pick)?.let { listOf(it) }
        is Op.Details -> list.rows().mapNotNull { (it.body as? RowBodyUi.Result)?.result?.details?.toggle }.pick(op.pick)?.let { listOf(it) }
        is Op.ModuleBand -> list.items.filterIsInstance<ListItemUi.ModuleHeader>().pick(op.pick)
            ?.let { listOf(KatachiIntent.ToggleModule(it.moduleId)) }
        is Op.MessageButton -> list.rows().flatMap { row ->
            (row.body as? RowBodyUi.Result)?.result?.let { result -> result.actions.map { it.intent } + result.files.mapNotNull { it.open } }.orEmpty()
        }.pick(op.pick)?.let { listOf(it) }
        else -> null
    }.orEmpty()
}

/** The buttons of the full-screen states: empty, error, first load. */
private fun messageIntentsOf(op: Op, body: BodyUi): List<KatachiIntent> {
    if (op !is Op.MessageButton && op !is Op.Details) return emptyList()
    val intents = when (body) {
        is BodyUi.Empty -> body.message.actions.map { it.intent }
        is BodyUi.Error -> if (op is Op.Details) listOfNotNull(body.message.details?.toggle) else body.message.actions.map { it.intent }
        is BodyUi.InitialLoading -> body.actions.map { it.intent }
        is BodyUi.Initializing, is BodyUi.Listing -> emptyList()
    }
    val index = (op as? Op.MessageButton)?.pick ?: 0
    return listOfNotNull(intents.pick(index))
}

private fun linkOf(field: FieldUi): LinkUi = when (field) {
    is FieldUi.Text -> field.link
    is FieldUi.Bool -> field.link
    is FieldUi.Choice -> field.link
    is FieldUi.Collapsed -> LinkUi.None
}
