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

// `reload` may refuse while the ViewModel's load job is still finishing; ask again after `RETRY_MILLIS` instead of losing the reload.
internal class AutoReload(
    private val scope: CoroutineScope,
    private val state: StateFlow<KatachiScreenState>,
    private val activity: GradleActivity,
    private val delayMillis: Long = AUTO_RELOAD_DELAY_MILLIS,
    private val reload: suspend () -> Boolean,
) {
    @Volatile private var pending: Job? = null

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

        const val RETRY_MILLIS: Long = 100L
    }
}

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
