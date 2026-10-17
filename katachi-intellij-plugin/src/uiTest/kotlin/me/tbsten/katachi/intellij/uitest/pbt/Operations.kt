package me.tbsten.katachi.intellij.uitest.pbt

import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.boolean
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
import me.tbsten.katachi.intellij.presentation.FocusTarget
import me.tbsten.katachi.intellij.presentation.LinkUi
import me.tbsten.katachi.intellij.presentation.NavKey
import me.tbsten.katachi.intellij.presentation.navigate
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

    // The directory candidates of capture fields (ide-review-screens plan 4.2).
    /** Types [CAPTURE_TEXTS]`[text]` into a capture field: a candidate, a part of one, a new name, empty, or what a capture refuses. */
    data class TypeCapture(val pick: Int, val text: Int) : Op
    /** Takes the [index]-th candidate the open popup of a capture field shows (what Enter or a click on it does: the field's text becomes it). */
    data class PickCandidate(val pick: Int, val index: Int) : Op
    /** The module band, by its keys: Enter, Space, Left or Right. */
    data class ToggleModuleByKey(val pick: Int, val key: Int) : Op
    /** Find Action's "Generate from Template" while the tool window is open: [KatachiIntent.FocusSearch]. */
    data object FocusSearchRequest : Op

    // What happens around the screen.
    /**
     * [View template] of an editor notification (C2): a template of the list by [pick], whether
     * the screen shows it or not, or one the list does not have (a notification decided on an
     * older list).
     */
    data class Reveal(val pick: Int) : Op
    /** The definition on disk becomes the catalog's world [world]; nothing reloads by itself. */
    data class Redefine(val world: Int) : Op
    data object Sync : Op
    data object DefinitionChanged : Op
    data class NextLoad(val outcome: RunOutcome) : Op
    data class NextRun(val outcome: RunOutcome) : Op
    data class ConflictAnswer(val choice: ConflictChoice, val waits: Boolean) : Op
    /**
     * From now on every candidate read waits before it reads, until [Release]: typing and the disk go on meanwhile, so that
     * a result can arrive for a question that is no longer the one asked (design 3.3, a stale result is dropped).
     */
    data object HoldCandidateReads : Op
    /** Every build, dialog and held candidate read that is waiting ends now (the reads in the opposite order they were asked). */
    data object Release : Op
    /**
     * A directory appears ([add]) or goes under the directory above a capture field ([pick] picks which, [name] its name in
     * [DISK_NAMES] or, for a removal, which of the children), and the files-changed event follows.
     */
    data class DiskChanged(val add: Boolean, val pick: Int, val name: Int) : Op
}

/** How a scripted Gradle run ends. */
internal enum class RunOutcome { Normal, Fails, Waits }

/**
 * Values typed into text fields: empty, blank, names, numbers, a line break, Japanese, and what a
 * capture refuses (two levels, climbing).
 */
internal val TYPED_VALUES = listOf("", "User", "Order", " ", "3", "abc", "x\ny", "日本語", "12345678901", "-1", "home/list", "..")

/** Directory names the fake disk holds; `.git` is one a field must never offer. */
internal val DISK_NAMES = listOf("home", "settings", "Profile", "alpha", "user", ".git", "日本語")

/** What a capture field is typed: the disk's names, a part of one (the popup's filter), a new name, empty, and what a capture refuses. */
internal val CAPTURE_TEXTS = DISK_NAMES + listOf("ho", "SE", "brand-new", "", " ", "a/b", "..")

/** The keys of a module band. */
private val BAND_KEYS = listOf(NavKey.Enter, NavKey.Space, NavKey.Left, NavKey.Right)

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
    3 to Arb.constant(Op.HoldCandidateReads),
    8 to Arb.bind(pick, Arb.int(CAPTURE_TEXTS.indices)) { target, text -> Op.TypeCapture(target, text) },
    6 to Arb.bind(pick, pick) { target, index -> Op.PickCandidate(target, index) },
    2 to Arb.bind(pick, Arb.int(BAND_KEYS.indices)) { target, key -> Op.ToggleModuleByKey(target, key) },
    1 to Arb.constant(Op.FocusSearchRequest),
    10 to Arb.bind(Arb.boolean(), pick, Arb.int(DISK_NAMES.indices)) { add, target, name -> Op.DiskChanged(add, target, name) },
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
        is Op.TypeCapture -> list.fields().filterIsInstance<FieldUi.Text>().filter { it.hint != null }.pick(op.pick)
            ?.let { listOf(KatachiIntent.Input(it.id, CAPTURE_TEXTS[op.text % CAPTURE_TEXTS.size])) }
        is Op.PickCandidate -> list.fields().filterIsInstance<FieldUi.Text>().filter { it.suggestions != null }.pick(op.pick)?.let { field ->
            // The popup lists the candidates the typed text is a prefix of (CandidateField), and cannot be opened on none.
            field.suggestions!!.values.filter { it.startsWith(field.value, ignoreCase = true) }.pick(op.index)?.let { listOf(KatachiIntent.Input(field.id, it)) }
        }
        is Op.ToggleModuleByKey -> list.items.filterIsInstance<ListItemUi.ModuleHeader>().pick(op.pick)
            ?.let { header -> navigate(list, FocusTarget.ModuleHeader(header.moduleId), BAND_KEYS[op.key % BAND_KEYS.size]).intent }
            ?.let { listOf(it) }
        Op.FocusSearchRequest -> listOf(KatachiIntent.FocusSearch)
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
