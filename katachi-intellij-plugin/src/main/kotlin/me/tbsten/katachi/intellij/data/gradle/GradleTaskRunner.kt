package me.tbsten.katachi.intellij.data.gradle

internal interface GradleRunListener {
    fun onLine(line: String) {}

    fun onTask(taskPath: String) {}
}

internal sealed interface GradleRunOutcome {
    data object Succeeded : GradleRunOutcome

    data object Failed : GradleRunOutcome
}

internal interface GradleTaskRunner {
    suspend fun run(request: GradleRunRequest, listener: GradleRunListener): GradleRunOutcome
}
