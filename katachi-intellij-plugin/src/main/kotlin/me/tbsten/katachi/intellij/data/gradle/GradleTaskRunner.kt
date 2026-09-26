package me.tbsten.katachi.intellij.data.gradle

/** Receives a running Gradle build's output, line by line. */
internal interface GradleRunListener {
    /** One line of output, without its line break. */
    fun onLine(line: String) {}

    /** Gradle started [taskPath] (`:arch:compileTestKotlin`): the "running > task" of the screen. */
    fun onTask(taskPath: String) {}
}

/** How a Gradle build ended. Cancellation is not an outcome: the runner throws `CancellationException`. */
internal sealed interface GradleRunOutcome {
    data object Succeeded : GradleRunOutcome

    /** Non-zero exit or a build failure; the output tells why. */
    data object Failed : GradleRunOutcome
}

/**
 * Runs Gradle the way the IDE does (ExternalSystem `runTask`, spec 06): the IDE's JDK, offline and
 * proxy settings, and the log in the Run tool window.
 *
 * Cancel by cancelling the calling coroutine; the implementation stops the build and rethrows
 * `CancellationException`. Faked in tests.
 */
internal interface GradleTaskRunner {
    suspend fun run(request: GradleRunRequest, listener: GradleRunListener): GradleRunOutcome
}
