package me.tbsten.katachi.intellij.presentation

import me.tbsten.katachi.intellij.model.ConflictQuestion
import me.tbsten.katachi.intellij.model.DescriptionSnapshot
import me.tbsten.katachi.intellij.model.GenerationItemResult
import me.tbsten.katachi.intellij.model.GenerationReport
import me.tbsten.katachi.intellij.model.KatachiModule
import me.tbsten.katachi.intellij.model.LoadFailure
import me.tbsten.katachi.intellij.model.ModuleTemplate
import me.tbsten.katachi.intellij.model.TemplateId
import me.tbsten.katachi.intellij.model.templatesOf

/** Why the list is empty (spec 02 "空状態"). An empty search result is derived by the UI instead. */
internal sealed interface EmptyReason {
    data object NoTemplates : EmptyReason

    data object NotGradle : EmptyReason

    data object NotSynced : EmptyReason

    data object NotInstalled : EmptyReason

    data class Outdated(val katachiVersion: String?) : EmptyReason
}

/** What fills the tool window. Loading and generating are overlays on [Ready] (see [KatachiScreenState]). */
internal sealed interface ScreenPhase {
    data object Initializing : ScreenPhase

    data class Empty(val reason: EmptyReason) : ScreenPhase

    /** A load failed with nothing cached to show; with a cache the list stays and a banner shows it. */
    data class LoadError(val failure: LoadFailure, val output: List<String>) : ScreenPhase

    data object Ready : ScreenPhase
}

/** A running `katachiInternalTemplatesJson`. */
internal data class LoadingState(
    /** No cache: the full-screen progress. Otherwise the thin bar over the list, which stays usable. */
    val isInitial: Boolean,
    /** The latest task Gradle reported, `> :arch:compileTestKotlin`. */
    val currentTask: String? = null,
)

/** One checked row during a generation. */
internal sealed interface GenerationRowStatus {
    data object Waiting : GenerationRowStatus

    data class Running(val taskPath: String?) : GenerationRowStatus

    /** The conflict dialog is open for this row ("existing files · waiting for you"). */
    data object AwaitingConflict : GenerationRowStatus

    data class Finished(val result: GenerationItemResult) : GenerationRowStatus
}

internal sealed interface GenerationState {
    /** [rows] in list order. [waitingForLoad]: Generate was pressed during a refresh (E-41). */
    data class Running(
        val rows: List<TemplateId>,
        val statuses: Map<TemplateId, GenerationRowStatus>,
        val waitingForLoad: Boolean = false,
        val conflict: ConflictQuestion? = null,
    ) : GenerationState {
        /** The 1-based "k / n" of the footer. */
        val position: Int get() = (rows.count { statuses[it] is GenerationRowStatus.Finished } + 1).coerceAtMost(rows.size)
    }

    /** The result screen. [localHistoryLabel] is what the footer names for undoing. */
    data class Finished(val report: GenerationReport, val localHistoryLabel: String?) : GenerationState
}

/**
 * Everything the tool window shows, UI-free. The UI renders it and sends [KatachiIntent]s back.
 */
internal data class KatachiScreenState(
    val phase: ScreenPhase = ScreenPhase.Initializing,
    /** The definition modules found in the synced data. */
    val modules: List<KatachiModule> = emptyList(),
    /** The list: cached until the first load finishes, then the latest load. */
    val snapshots: List<DescriptionSnapshot> = emptyList(),
    val loading: LoadingState? = null,
    /** A load failed while a cached list stays on screen (the banner of E-04, E-34). */
    val loadErrorBanner: LoadFailure? = null,
    /** Checked templates the last reload removed: "Repository is gone" (E-45). */
    val removedTemplates: List<TemplateId> = emptyList(),
    val form: FormState = FormState(),
    val searchQuery: String = "",
    val generation: GenerationState? = null,
    val view: ViewState = ViewState(),
) {
    val rows: List<ModuleTemplate> get() = templatesOf(snapshots)

    val busy: BusyState
        get() = when {
            generation is GenerationState.Running -> BusyState.Generating
            loading?.isInitial == true -> BusyState.InitialLoading
            else -> BusyState.Idle
        }

    val generateBlocker: GenerateBlocker? get() = generateBlockerOf(rows, form, busy)
}
