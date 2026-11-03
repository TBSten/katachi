package me.tbsten.katachi.intellij.data.gradle

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// Runs one build at a time: concurrent builds in the definition module would fight over the same `build/`.
internal class SerialGradleTaskRunner(
    private val delegate: GradleTaskRunner,
    private val activity: GradleActivity? = null,
) : GradleTaskRunner {
    private val mutex = Mutex()

    override suspend fun run(request: GradleRunRequest, listener: GradleRunListener): GradleRunOutcome {
        suspend fun inTurn() = mutex.withLock { delegate.run(request, listener) }
        return activity?.counting { inTurn() } ?: inTurn()
    }
}
