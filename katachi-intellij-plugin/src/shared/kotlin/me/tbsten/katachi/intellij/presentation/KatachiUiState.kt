package me.tbsten.katachi.intellij.presentation

import me.tbsten.katachi.intellij.model.ModuleId
import me.tbsten.katachi.intellij.model.TemplateId

/*
 * What the Composables draw: immutable values with every text already worded. The UI makes no
 * decision beyond layout; it renders these and sends back the KatachiIntent they carry.
 */

/** Everything the tool window shows. [titleAction] is for `ToolWindow.setTitleActions` (stage 3). */
internal data class KatachiUiState(
    val titleAction: TitleAction,
    val body: BodyUi,
)

/** ⟳ normally, ■ while a load runs (spec 02). */
internal enum class TitleAction { Reload, Stop }

/** A button or a link: its words and what pressing it asks for. */
internal data class ActionUi(val label: String, val intent: KatachiIntent)

internal sealed interface BodyUi {
    data class Initializing(val message: String) : BodyUi

    /** The first load without a cache: progress, the latest Gradle task, cancel and the log. */
    data class InitialLoading(
        val title: String,
        val currentTask: String?,
        val note: String,
        val actions: List<ActionUi>,
    ) : BodyUi

    data class Empty(val message: MessageUi) : BodyUi

    data class Error(val message: MessageUi) : BodyUi

    data class Listing(val list: ListUi) : BodyUi
}

/** An empty state or an error: what happened, what to do, and optional details (spec 02). */
internal data class MessageUi(
    val title: String,
    val isError: Boolean,
    val body: List<String>,
    val actions: List<ActionUi>,
    val details: DetailsUi? = null,
)

/** "▸ Details": a toggle and the lines it opens. */
internal data class DetailsUi(
    val label: String,
    val lines: List<String>,
    val isOpen: Boolean,
    val toggle: KatachiIntent,
)

internal enum class BannerKind { Info, Warning, Error }

/** A band above the list: a stale error, "the definition changed", "Repository is gone". */
internal data class BannerUi(
    val kind: BannerKind,
    val text: String,
    val actions: List<ActionUi>,
    val dismiss: KatachiIntent?,
)

/** The thin bar of a refresh over a cached list. */
internal data class RefreshUi(val label: String, val cancel: ActionUi)

internal data class ListUi(
    val refresh: RefreshUi?,
    val banners: List<BannerUi>,
    val search: SearchUi,
    val items: List<ListItemUi>,
    /** The search matched nothing and nothing is checked (E-28). */
    val emptySearch: MessageUi?,
    val footer: FooterUi,
)

internal data class SearchUi(val query: String, val placeholder: String, val enabled: Boolean)

/** One entry of the list. [key] is stable across states, so a row keeps its remembered UI state. */
internal sealed interface ListItemUi {
    val key: String

    /** Only with two or more definition modules; sticks to the top while scrolling (E-05). */
    data class ModuleHeader(
        val moduleId: ModuleId,
        val title: String,
        val counter: String,
        val isCollapsed: Boolean,
    ) : ListItemUi {
        override val key: String get() = "module:${moduleId.linkedRootPath}:${moduleId.gradlePath}"
    }

    data class GroupHeader(override val key: String, val title: String) : ListItemUi

    data class Row(val row: TemplateRowUi) : ListItemUi {
        override val key: String get() = "row:${row.id.module.linkedRootPath}:${row.id.module.gradlePath}:${row.id.template}"
    }
}

/** The left end of a row: a checkbox while editable, a status icon while generating or after. */
internal sealed interface RowLeadUi {
    data class Check(val checked: Boolean, val enabled: Boolean) : RowLeadUi

    data class Status(val status: RowStatus) : RowLeadUi
}

internal enum class RowStatus { Done, Running, Waiting, AwaitingConflict, Failed, Skipped, Stopped, Interrupted, NotRun }

/** ⚠ (a file without a target, E-27) or ⛔ (cannot be checked, E-07, E-36). */
internal enum class RowMarker { None, Warning, Blocked }

internal data class TemplateRowUi(
    val id: TemplateId,
    val name: String,
    val title: String?,
    val tooltip: String?,
    val lead: RowLeadUi,
    val marker: RowMarker,
    val markerTooltip: String?,
    /** The status during a generation ("実行中 > …"); empty while the list is editable. */
    val trailing: String,
    /** Grey: cannot be checked, or checked but outside the search. */
    val isFaint: Boolean,
    /** "outside search, selected" next to the name (E-16). */
    val note: String?,
    /** "filled 2/3" on the right of a checked row whose form is folded (spec 04). */
    val filled: String?,
    /** ▾ / ▸ of a checked row: `null` when there is no form to fold. */
    val isExpanded: Boolean?,
    val menu: List<ActionUi>,
    val body: RowBodyUi?,
)

internal sealed interface RowBodyUi {
    data class Form(val form: FormUi) : RowBodyUi

    /** "Show cause" of a preview failure. */
    data class Cause(val lines: List<String>) : RowBodyUi

    /** Under the running row: the task path and the arguments sent. */
    data class Running(val taskLine: String, val argsLine: String) : RowBodyUi

    data class Result(val result: RowResultUi) : RowBodyUi
}

internal data class FormUi(
    /** `summary` of the template, the first line of the form. */
    val summary: String?,
    val fields: List<FieldUi>,
)

/** 🔗 on a linked field, the faint unlink icon on one whose link was broken (E-14). */
internal sealed interface LinkUi {
    data object None : LinkUi

    data object Linked : LinkUi

    data class Unlinked(val relink: KatachiIntent) : LinkUi
}

internal sealed interface FieldUi {
    val id: FieldId
    val label: String
    val isRequired: Boolean

    data class Text(
        override val id: FieldId,
        override val label: String,
        override val isRequired: Boolean,
        val value: String,
        /** The default shown faint in the empty field, placeholders replaced. */
        val placeholder: String?,
        val isNumber: Boolean,
        val error: String?,
        val link: LinkUi,
        /** `null` for Int fields, which have no multi-line mode. */
        val isMultiline: Boolean?,
        /** What the `>` next to the field does: "enter on several lines" / "back to one line". */
        val multilineTooltip: String?,
        /** A faint note under the field; a capture's says where its value goes. */
        val hint: String? = null,
    ) : FieldUi

    data class Bool(
        override val id: FieldId,
        override val label: String,
        val checked: Boolean,
        val link: LinkUi,
    ) : FieldUi {
        override val isRequired: Boolean get() = false
    }

    data class Choice(
        override val id: FieldId,
        override val label: String,
        override val isRequired: Boolean,
        val options: List<String>,
        /** `-1` shows [placeholder] ("choose one"). */
        val selectedIndex: Int,
        val placeholder: String,
        val error: String?,
        val link: LinkUi,
    ) : FieldUi

    /** A parameter of an untaken branch: one grey line, its input kept but not sent (E-08). */
    data class Collapsed(override val id: FieldId, override val label: String) : FieldUi {
        override val isRequired: Boolean get() = false
    }
}

internal data class RowResultUi(
    val files: List<ResultFileUi>,
    val message: List<String>,
    val isError: Boolean,
    val actions: List<ActionUi>,
    val details: DetailsUi?,
)

internal data class ResultFileUi(val name: String, val badge: String, val note: String?, val open: KatachiIntent?)

internal sealed interface FooterUi {
    data class Form(
        /** The first reason Generate is disabled; pressing it reveals the field when there is one. */
        val reason: String?,
        val reasonTarget: FieldId?,
        val onExistingLabel: String,
        val onExistingOptions: List<ActionUi>,
        val onExistingIndex: Int,
        val overwriteWarning: String?,
        val generate: ActionUi,
        val generateEnabled: Boolean,
        val generateTooltip: String?,
    ) : FooterUi

    data class Generating(val label: String, val progress: Float?, val cancel: ActionUi) : FooterUi

    data class Result(val summary: String, val isComplete: Boolean, val undo: String?, val actions: List<ActionUi>) : FooterUi
}
