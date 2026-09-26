package me.tbsten.katachi.intellij.ide

import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.process.ProcessOutputType
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.service.internal.ExternalSystemProcessingManager
import com.intellij.openapi.externalSystem.task.TaskCallback
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.externalSystem.util.task.TaskExecutionSpec
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import me.tbsten.katachi.intellij.data.gradle.GradleRunListener
import me.tbsten.katachi.intellij.data.gradle.GradleRunOutcome
import me.tbsten.katachi.intellij.data.gradle.GradleRunRequest
import me.tbsten.katachi.intellij.data.gradle.GradleTaskRunner
import me.tbsten.katachi.intellij.data.gradle.OutputLineSplitter
import me.tbsten.katachi.intellij.data.gradle.stripAnsi
import me.tbsten.katachi.intellij.data.gradle.taskPathOf
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Runs Gradle through ExternalSystem's `runTask` (spec 06): the IDE's Gradle JDK, offline and proxy
 * settings apply, and the log lands in the Run tool window with the status bar's progress.
 *
 * The output arrives in chunks, stdout and stderr interleaved, and is split into lines per stream
 * here. Cancelling the calling coroutine cancels the ExternalSystem task, which stops the Gradle build
 * (spike S-6 decides whether the processor's child JVM stops with it), and waits a while for it to
 * end, so that the next build does not start over it and what it wrote is on disk.
 */
internal class ExternalSystemGradleTaskRunner(private val project: Project) : GradleTaskRunner {
    override suspend fun run(request: GradleRunRequest, listener: GradleRunListener): GradleRunOutcome {
        val settings = ExternalSystemTaskExecutionSettings().apply {
            externalProjectPath = request.linkedRootPath.toString()
            taskNames = request.taskNames
            externalSystemIdString = GradleConstants.SYSTEM_ID.id
        }
        val finished = CompletableDeferred<Boolean>()
        val taskId = AtomicReference<ExternalSystemTaskId?>()
        val cancelled = AtomicBoolean(false)
        val lines = StreamLines { raw ->
            val line = stripAnsi(raw)
            taskPathOf(line)?.let(listener::onTask)
            listener.onLine(line)
        }
        val events = object : ExternalSystemTaskNotificationListener {
            override fun onStart(projectPath: String, id: ExternalSystemTaskId) {
                taskId.set(id)
                // Cancelled before the task had an id: stop it now that it has one.
                if (cancelled.get()) cancelTask(id)
            }

            override fun onTaskOutput(id: ExternalSystemTaskId, text: String, outputType: ProcessOutputType) {
                lines.append(text, outputType.isStderr)
            }
        }
        val spec = TaskExecutionSpec.create()
            .withProject(project)
            .withSystemId(GradleConstants.SYSTEM_ID)
            .withExecutorId(DefaultRunExecutor.EXECUTOR_ID)
            .withSettings(settings)
            .withProgressExecutionMode(ProgressExecutionMode.IN_BACKGROUND_ASYNC)
            .withActivateToolWindowBeforeRun(false)
            .withActivateToolWindowOnFailure(false)
            .dontNavigateToError()
            .withListener(events)
            .withCallback(object : TaskCallback {
                override fun onSuccess() {
                    finished.complete(true)
                }

                override fun onFailure() {
                    finished.complete(false)
                }
            })
            .build()
        withContext(Dispatchers.EDT) { writeIntentReadAction { ExternalSystemUtil.runTask(spec) } }
        val succeeded = try {
            finished.await()
        } catch (e: CancellationException) {
            cancelled.set(true)
            taskId.get()?.let(::cancelTask)
            withContext(NonCancellable) { withTimeoutOrNull(STOP_TIMEOUT_MILLIS) { finished.await() } }
            throw e
        }
        lines.flush()
        return if (succeeded) GradleRunOutcome.Succeeded else GradleRunOutcome.Failed
    }

    private fun cancelTask(id: ExternalSystemTaskId) {
        ExternalSystemProcessingManager.getInstance().findTask(id)?.cancel()
    }

    private companion object {
        /** How long a cancelled build gets to end before the caller moves on anyway. */
        const val STOP_TIMEOUT_MILLIS = 10_000L
    }
}

/**
 * One [OutputLineSplitter] per stream, so that a stderr chunk arriving in the middle of a stdout
 * line does not cut it (katachi's `[FAILED] template` body is indented stdout).
 */
private class StreamLines(onLine: (String) -> Unit) {
    private val stdout = OutputLineSplitter(onLine)
    private val stderr = OutputLineSplitter(onLine)

    @Synchronized
    fun append(chunk: String, isStderr: Boolean) {
        (if (isStderr) stderr else stdout).append(chunk)
    }

    @Synchronized
    fun flush() {
        stdout.flush()
        stderr.flush()
    }
}
