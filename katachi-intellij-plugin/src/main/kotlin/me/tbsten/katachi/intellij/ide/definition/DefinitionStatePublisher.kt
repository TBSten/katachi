package me.tbsten.katachi.intellij.ide.definition

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import me.tbsten.katachi.intellij.model.DefinitionState
import me.tbsten.katachi.intellij.model.StaleReason
import me.tbsten.katachi.intellij.model.markStale
import me.tbsten.katachi.intellij.presentation.DefinitionStateSource
import me.tbsten.katachi.intellij.presentation.KatachiToolWindowViewModel
import me.tbsten.katachi.intellij.presentation.nextDefinitionState
import java.time.Instant

/**
 * The definition model of one project as a [StateFlow] (design 3, 4.3), derived from the tool window's
 * ViewModel, the one place that loads: each change of what the definitions depend on
 * ([DefinitionStateSource]) moves [state] by [nextDefinitionState], and a change of the definition's files
 * marks it stale ([markStale]) until the next read. Nothing here runs Gradle.
 *
 * ```kotlin
 * val publisher = DefinitionStatePublisher(scope, viewModel, loadsWithoutUser = { true })
 * publisher.state.collect { state -> redraw(state) }
 * ```
 */
internal class DefinitionStatePublisher(
    scope: CoroutineScope,
    viewModel: KatachiToolWindowViewModel,
    private val loadsWithoutUser: () -> Boolean,
    private val clock: () -> Instant = Instant::now,
) {
    private val lock = Any()
    private val mutableState = MutableStateFlow<DefinitionState>(DefinitionState.NotLoaded)
    private var lastSource: DefinitionStateSource? = null

    /** The definition's files changed after the running load started: what it reads may be from before the change. */
    private var changedDuringLoad = false

    val state: StateFlow<DefinitionState> = mutableState.asStateFlow()

    init {
        scope.launch {
            combine(viewModel.state, viewModel.loadStarted) { screen, started -> DefinitionStateSource.of(screen, started, loadsWithoutUser()) }
                .distinctUntilChanged()
                .flowOn(Dispatchers.Default)
                .collect(::apply)
        }
    }

    /**
     * The definition's files changed: the answer stays and is marked stale until the next read (design 4.8). A change
     * while a load runs is remembered: the load may have read the files before it, so its answer is stale too.
     */
    fun markStale() = synchronized(lock) {
        if (mutableState.value is DefinitionState.Loading) changedDuringLoad = true
        mutableState.value = mutableState.value.markStale(clock(), StaleReason.DefinitionChanged)
    }

    private fun apply(source: DefinitionStateSource) = synchronized(lock) {
        val now = clock()
        val wasLoading = lastSource?.loading == true
        var next = nextDefinitionState(mutableState.value, lastSource, source, now)
        if (wasLoading && !source.loading) {
            if (changedDuringLoad && next is DefinitionState.Ready) next = next.markStale(now, StaleReason.DefinitionChanged)
            changedDuringLoad = false
        }
        if (!wasLoading && source.loading) changedDuringLoad = false
        mutableState.value = next
        lastSource = source
    }
}
