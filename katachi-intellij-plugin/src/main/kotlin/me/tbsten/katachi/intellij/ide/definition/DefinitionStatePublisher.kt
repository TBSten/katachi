package me.tbsten.katachi.intellij.ide.definition

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import me.tbsten.katachi.intellij.model.DefinitionErrors
import me.tbsten.katachi.intellij.model.DefinitionErrorsEvent
import me.tbsten.katachi.intellij.model.DefinitionState
import me.tbsten.katachi.intellij.model.StaleReason
import me.tbsten.katachi.intellij.model.after
import me.tbsten.katachi.intellij.model.markStale
import me.tbsten.katachi.intellij.presentation.DefinitionStateSource
import me.tbsten.katachi.intellij.presentation.KatachiToolWindowViewModel
import me.tbsten.katachi.intellij.presentation.definitionErrorsEventOf
import me.tbsten.katachi.intellij.presentation.nextDefinitionState
import java.time.Instant

internal class DefinitionStatePublisher(
    scope: CoroutineScope,
    private val viewModel: KatachiToolWindowViewModel,
    private val loadsWithoutUser: () -> Boolean,
    private val clock: () -> Instant = Instant::now,
) {
    private val lock = Any()
    private val mutableState = MutableStateFlow<DefinitionState>(DefinitionState.NotLoaded)
    private var lastSource: DefinitionStateSource? = null

    private val applied = MutableStateFlow<DefinitionStateSource?>(null)

    private var changedDuringLoad = false

    private val mutableErrors = MutableStateFlow(DefinitionErrors.None)

    val state: StateFlow<DefinitionState> = mutableState.asStateFlow()

    val errors: StateFlow<DefinitionErrors> = mutableErrors.asStateFlow()

    init {
        scope.launch {
            combine(viewModel.state, viewModel.loadStarted) { screen, started -> DefinitionStateSource.of(screen, started, loadsWithoutUser()) }
                .distinctUntilChanged()
                .flowOn(Dispatchers.Default)
                .collect(::apply)
        }
    }

    // A change during a running load is remembered: that load may have read the files before it, so its answer is stale too.
    fun markStale() = synchronized(lock) {
        if (mutableState.value is DefinitionState.Loading) changedDuringLoad = true
        mutableState.value = mutableState.value.markStale(clock(), StaleReason.DefinitionChanged)
        mutableErrors.value = mutableErrors.value.after(DefinitionErrorsEvent.DefinitionChanged)
    }

    private fun apply(source: DefinitionStateSource) = synchronized(lock) {
        val now = clock()
        val wasLoading = lastSource?.loading == true
        var next = nextDefinitionState(mutableState.value, lastSource, source, now)
        val changedWhileItRan = wasLoading && !source.loading && changedDuringLoad
        if (wasLoading && !source.loading) {
            if (changedDuringLoad && next is DefinitionState.Ready) next = next.markStale(now, StaleReason.DefinitionChanged)
            changedDuringLoad = false
        }
        if (!wasLoading && source.loading) changedDuringLoad = false
        mutableState.value = next
        definitionErrorsEventOf(lastSource, source, now)?.let { event ->
            var errors = mutableErrors.value.after(event)
            // A rejection read the files as they were when the load began: if they changed while it ran, it is from before the change.
            if (changedWhileItRan && event is DefinitionErrorsEvent.LoadRejected) errors = errors.after(DefinitionErrorsEvent.DefinitionChanged)
            mutableErrors.value = errors
        }
        lastSource = source
        applied.value = source
    }

    /** [state] once what the ViewModel shows now has been applied: the collector above runs behind the ViewModel. */
    suspend fun settled(): DefinitionState {
        // Compared with what the ViewModel shows at each apply, not once: the collector can skip a value the ViewModel moved past.
        applied.first { it == DefinitionStateSource.of(viewModel.state.value, viewModel.loadStarted.value, loadsWithoutUser()) }
        return state.value
    }
}
