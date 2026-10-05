package me.tbsten.katachi.intellij.ide

import com.intellij.openapi.Disposable
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemProgressNotificationManager
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.tbsten.katachi.intellij.data.gradle.GradleActivity
import me.tbsten.katachi.intellij.presentation.GenerationState
import me.tbsten.katachi.intellij.presentation.KatachiScreenState

/**
 * The reload that follows a save of the definition (design 4.8, decision U-1): [AUTO_RELOAD_DELAY_MILLIS]
 * after the last change of a definition module's test sources, once no Gradle build or sync of the IDE
 * runs ([GradleActivity]) and no load or generation of the tool window does, [reload] runs. A change in
 * the meantime starts the wait again. Whether to schedule at all (the setting, a load started in this
 * session) is the caller's decision. [reload] says whether it started: the ViewModel refuses while its load job
 * is still finishing (a detection that has not set `loading` yet, the instant after a load ends), and then
 * this asks again [RETRY_MILLIS] later rather than losing the reload.
 *
 * ```kotlin
 * val autoReload = AutoReload(scope, viewModel.state, activity) { viewModel.reloadIfIdle() }
 * autoReload.schedule()
 * ```
 */
internal class AutoReload(
    private val scope: CoroutineScope,
    private val state: StateFlow<KatachiScreenState>,
    private val activity: GradleActivity,
    private val delayMillis: Long = AUTO_RELOAD_DELAY_MILLIS,
    private val reload: suspend () -> Boolean,
) {
    @Volatile private var pending: Job? = null

    /** Whether a reload is waiting (for the delay or for Gradle). */
    val isPending: Boolean get() = pending?.isActive == true

    fun schedule() {
        pending?.cancel()
        pending = scope.launch {
            delay(delayMillis)
            while (true) {
                activity.awaitIdle()
                state.first { it.loading == null && it.generation !is GenerationState.Running }
                if (reload()) return@launch
                delay(RETRY_MILLIS)
            }
        }
    }

    companion object {
        const val AUTO_RELOAD_DELAY_MILLIS: Long = 2_000L

        /** How long to wait before asking again when [reload] refused. */
        const val RETRY_MILLIS: Long = 100L
    }
}

/**
 * Feeds [activity] with the Gradle syncs and builds the IDE runs for [project] (design U-12), from
 * ExternalSystem's progress notifications, until [parent] is disposed. Found with `javap` on 2026.1:
 * `ExternalSystemProgressNotificationManager.addNotificationListener(listener, disposable)` sees every
 * ExternalSystem task (syncs and task runs, the plugin's own included), with the task's project in its id.
 * Returns whether the listener could be added; without it only the plugin's own builds are waited for.
 */
internal fun watchIdeGradle(project: Project, activity: GradleActivity, parent: Disposable): Boolean {
    val projectId = sdkCall("read the id of the project") { ExternalSystemTaskId.getProjectId(project) }.getOrNull() ?: return false
    val listener = object : ExternalSystemTaskNotificationListener {
        override fun onStart(projectPath: String, id: ExternalSystemTaskId) {
            if (id.ideProjectId == projectId) activity.externalStarted(id)
        }

        override fun onEnd(projectPath: String, id: ExternalSystemTaskId) {
            activity.externalEnded(id)
        }
    }
    return sdkCall("listen to the Gradle tasks of the IDE") {
        ExternalSystemProgressNotificationManager.getInstance().addNotificationListener(listener, parent)
    }.getOrNull() == true
}
