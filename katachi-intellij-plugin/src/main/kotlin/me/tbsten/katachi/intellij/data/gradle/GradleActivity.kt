package me.tbsten.katachi.intellij.data.gradle

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

/**
 * Whether Gradle is running in the IDE for this project: the plugin's own builds (through [track]) and
 * the builds and syncs the IDE reports ([externalStarted] / [externalEnded], fed from ExternalSystem's
 * progress notifications, design U-12). The automatic reload after a save waits for [awaitIdle] so that
 * it does not start a build over one the user started.
 *
 * A build started in a terminal is not seen; Gradle's own lock puts the two in a row.
 *
 * ```kotlin
 * val activity = GradleActivity()
 * val runner = SerialGradleTaskRunner(ports.runner, activity) // or activity.track(runner) around a queue
 * activity.awaitIdle()
 * ```
 */
internal class GradleActivity {
    private val lock = Any()
    private var own = 0
    private val external = HashSet<Any>()
    private val mutableBusy = MutableStateFlow(false)

    /** Whether a build of this plugin or of the IDE is running or queued now. */
    val busy: StateFlow<Boolean> = mutableBusy.asStateFlow()

    /**
     * [runner], whose builds count as running. Wrap a runner that queues from the outside (or give the queue
     * this activity, as `SerialGradleTaskRunner` takes it) for the wait to count; inside a queue only the turn counts.
     */
    fun track(runner: GradleTaskRunner): GradleTaskRunner = object : GradleTaskRunner {
        override suspend fun run(request: GradleRunRequest, listener: GradleRunListener): GradleRunOutcome =
            counting { runner.run(request, listener) }
    }

    /** Runs [block] counted as one running build, however it ends. */
    suspend fun <T> counting(block: suspend () -> T): T {
        change { own++ }
        try {
            return block()
        } finally {
            change { own = (own - 1).coerceAtLeast(0) }
        }
    }

    /** The IDE started the task [id] (a sync or a build). The same id twice counts once. */
    fun externalStarted(id: Any) {
        change { external += id }
    }

    /** The task [id] ended. An id that never started is ignored. */
    fun externalEnded(id: Any) {
        change { external -= id }
    }

    /** Returns once nothing runs. Returns at once when nothing does now. */
    suspend fun awaitIdle() {
        busy.first { !it }
    }

    /** Applies [block] and publishes [busy], under one lock so that two threads never publish out of order. */
    private inline fun change(block: () -> Unit) = synchronized(lock) {
        block()
        mutableBusy.value = own > 0 || external.isNotEmpty()
    }
}
