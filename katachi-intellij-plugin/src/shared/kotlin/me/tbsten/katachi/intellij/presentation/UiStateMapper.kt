package me.tbsten.katachi.intellij.presentation

import me.tbsten.katachi.intellij.model.GradleFailure
import me.tbsten.katachi.intellij.model.LoadFailure
import java.time.Duration
import java.time.Instant

/**
 * [state] as the Composables draw it. [now] words "last loaded 10 minutes ago", so a preview can
 * fix it.
 */
internal fun uiStateOf(state: KatachiScreenState, strings: KatachiStrings, now: Instant): KatachiUiState {
    val loading = state.loading
    val body = when {
        loading != null && loading.isInitial -> BodyUi.InitialLoading(
            title = strings.loadingTitle,
            currentTask = loading.currentTask?.let { "> $it" },
            note = strings.loadingNote,
            actions = listOf(ActionUi(strings.cancel, KatachiIntent.CancelLoad), ActionUi(strings.showLog, KatachiIntent.ShowLog)),
        )
        state.phase == ScreenPhase.Ready -> BodyUi.Listing(listUiOf(state, strings, now))
        else -> when (val phase = state.phase) {
            is ScreenPhase.Empty -> BodyUi.Empty(emptyMessageOf(phase.reason, strings))
            is ScreenPhase.LoadError -> BodyUi.Error(errorMessageOf(phase.failure, state.view, strings))
            ScreenPhase.Initializing, ScreenPhase.Ready -> BodyUi.Initializing(strings.initializing)
        }
    }
    return KatachiUiState(titleAction = titleActionOf(state), body = body)
}

/** ■ while a load runs, else ⟳ (spec 04). The title bar asks it often, so it skips the rest of [uiStateOf]. */
internal fun titleActionOf(state: KatachiScreenState): TitleAction = if (state.loading != null) TitleAction.Stop else TitleAction.Reload

private fun listUiOf(state: KatachiScreenState, strings: KatachiStrings, now: Instant): ListUi {
    val items = listItemsOf(state, strings)
    val loading = state.loading
    val query = state.searchQuery.trim()
    return ListUi(
        refresh = if (loading != null && !loading.isInitial) {
            val lastLoaded = state.snapshots.maxOfOrNull { it.loadedAt }
            RefreshUi(
                label = strings.refreshing(lastLoaded?.let { relativeTimeOf(it, now, strings) } ?: strings.justNow),
                cancel = ActionUi(strings.cancel, KatachiIntent.CancelLoad),
            )
        } else {
            null
        },
        banners = bannersOf(state, strings),
        search = SearchUi(state.searchQuery, strings.searchPlaceholder, enabled = state.generation == null),
        items = items,
        emptySearch = if (query.isNotEmpty() && items.none { it is ListItemUi.Row }) {
            MessageUi(
                title = strings.searchEmptyTitle(query),
                isError = false,
                body = emptyList(),
                actions = listOf(ActionUi(strings.clearSearch, KatachiIntent.Search(""))),
            )
        } else {
            null
        },
        footer = footerUiOf(state, strings),
    )
}

private fun bannersOf(state: KatachiScreenState, strings: KatachiStrings): List<BannerUi> = listOfNotNull(
    state.loadErrorBanner?.let { failure ->
        val action = if (failure is LoadFailure.Gradle && failure.failure is GradleFailure.TaskNotFound) {
            ActionUi(strings.sync, KatachiIntent.SyncGradle)
        } else {
            ActionUi(strings.showLog, KatachiIntent.ShowLog)
        }
        BannerUi(BannerKind.Error, strings.staleListBanner, listOf(action), KatachiIntent.DismissLoadError)
    },
    BannerUi(BannerKind.Info, strings.definitionChanged, listOf(ActionUi(strings.reload, KatachiIntent.Reload)), KatachiIntent.DismissDefinitionChanged)
        .takeIf { state.view.definitionChanged },
    state.removedTemplates.takeIf { it.isNotEmpty() }?.let { removed ->
        BannerUi(BannerKind.Warning, strings.removedTemplates(removed.map { it.roleName.substringAfterLast('/') }), emptyList(), KatachiIntent.DismissRemovedTemplates)
    },
)

internal fun relativeTimeOf(then: Instant, now: Instant, strings: KatachiStrings): String {
    val elapsed = Duration.between(then, now)
    return when {
        elapsed.toMinutes() < 1 -> strings.justNow
        elapsed.toHours() < 1 -> strings.minutesAgo(elapsed.toMinutes())
        elapsed.toDays() < 1 -> strings.hoursAgo(elapsed.toHours())
        else -> strings.daysAgo(elapsed.toDays())
    }
}

private fun emptyMessageOf(reason: EmptyReason, strings: KatachiStrings): MessageUi {
    val reload = ActionUi(strings.reload, KatachiIntent.Reload)
    val sync = ActionUi(strings.sync, KatachiIntent.SyncGradle)
    return when (reason) {
        EmptyReason.NoTemplates -> MessageUi(
            strings.noTemplatesTitle,
            isError = false,
            body = listOf(strings.noTemplatesBody),
            actions = listOf(ActionUi(strings.howToWrite, KatachiIntent.OpenDocs(DocsPage.WritingTemplates)), reload),
        )
        EmptyReason.NotGradle -> MessageUi(strings.notGradleTitle, isError = false, body = listOf(strings.notGradleBody), actions = emptyList())
        EmptyReason.NotSynced -> MessageUi(strings.notSyncedTitle, isError = false, body = listOf(strings.notSyncedBody), actions = listOf(sync))
        EmptyReason.NotInstalled -> MessageUi(
            strings.notInstalledTitle,
            isError = false,
            body = listOf(strings.notInstalledBody),
            actions = listOf(ActionUi(strings.installGuide, KatachiIntent.OpenDocs(DocsPage.Install)), sync),
        )
        is EmptyReason.Outdated -> MessageUi(
            strings.outdatedTitle(reason.katachiVersion),
            isError = false,
            body = listOf(strings.outdatedBody),
            actions = listOf(ActionUi(strings.updateGuide, KatachiIntent.OpenDocs(DocsPage.Update)), sync),
        )
    }
}

/** The error screen: what happened, what to do, and the details (spec 02 "エラー"). */
private fun errorMessageOf(failure: LoadFailure, view: ViewState, strings: KatachiStrings): MessageUi {
    val reload = ActionUi(strings.reload, KatachiIntent.Reload)
    val showLog = ActionUi(strings.showLog, KatachiIntent.ShowLog)
    fun details(lines: List<String>) = lines.takeIf { it.isNotEmpty() }?.let {
        DetailsUi(strings.details, it, DetailsKey.LoadError in view.openDetails, KatachiIntent.ToggleDetails(DetailsKey.LoadError))
    }
    fun error(body: List<String>, actions: List<ActionUi>, detailLines: List<String>) =
        MessageUi(strings.loadErrorTitle, isError = true, body = body, actions = actions, details = details(detailLines))
    return when (failure) {
        is LoadFailure.Gradle -> when (val gradle = failure.failure) {
            is GradleFailure.CompilationFailed -> error(listOf(strings.compileFailed, strings.compileFailedHint), listOf(showLog, reload), gradle.details)
            is GradleFailure.TaskNotFound ->
                error(listOf(strings.staleSync, strings.staleSyncHint), listOf(ActionUi(strings.sync, KatachiIntent.SyncGradle), reload), gradle.details)
            is GradleFailure.ArchitectureNotSet -> error(
                listOf(strings.architectureNotSet, strings.architectureNotSetHint),
                listOf(ActionUi(strings.openBuildScript, KatachiIntent.OpenBuildScript), reload),
                gradle.details,
            )
            is GradleFailure.ProcessorRejected, is GradleFailure.Other ->
                error(listOf(strings.gradleFailed, strings.gradleFailedHint), listOf(showLog, reload), gradle.details)
        }
        is LoadFailure.JsonMissing -> error(listOf(strings.jsonMissing), listOf(showLog, reload), listOf(failure.path.toUri().toString()))
        is LoadFailure.MalformedJson -> error(
            listOf(strings.jsonMalformed),
            listOf(ActionUi(strings.openJson, KatachiIntent.OpenFile(failure.path)), reload),
            listOf(failure.message, failure.path.toUri().toString()),
        )
        is LoadFailure.IncompatibleJson -> error(
            listOf(strings.jsonIncompatible, strings.jsonIncompatibleHint),
            listOf(ActionUi(strings.openJson, KatachiIntent.OpenFile(failure.path)), reload),
            listOf("${failure.location}: ${failure.message}", failure.path.toUri().toString()),
        )
        LoadFailure.Cancelled -> MessageUi(strings.loadCancelled, isError = false, body = emptyList(), actions = listOf(reload))
    }
}
