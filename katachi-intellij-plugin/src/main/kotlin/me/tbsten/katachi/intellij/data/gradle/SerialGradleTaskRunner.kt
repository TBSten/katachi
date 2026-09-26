package me.tbsten.katachi.intellij.data.gradle

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A [GradleTaskRunner] that runs one build at a time: a second request waits until the first ends.
 *
 * Loading, generating and "show cause" all run in the definition module, and two builds side by
 * side would fight over the same `build/` (spec 06). Waiting is cancellable like the run itself.
 *
 * ```kotlin
 * val runner = SerialGradleTaskRunner(ExternalSystemGradleTaskRunner(project))
 * ```
 */
internal class SerialGradleTaskRunner(private val delegate: GradleTaskRunner) : GradleTaskRunner {
    private val mutex = Mutex()

    override suspend fun run(request: GradleRunRequest, listener: GradleRunListener): GradleRunOutcome =
        mutex.withLock { delegate.run(request, listener) }
}
