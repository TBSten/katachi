package me.tbsten.katachi.intellij.ide.spike

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.PlatformTestUtil
import java.util.concurrent.Future

// TODO: S2 spike only (throwaway). The results are written up in .local/idea-plugin/impl/spike-ide.md.

/** One observation, printed so that it lands in the test report's system-out. */
internal fun spike(key: String, value: Any?) {
    println("SPIKE[$key] $value")
}

/** Runs [block] on a pooled thread inside a read action, as the platform calls BGT code, and waits pumping the EDT. */
internal fun <T> onBgtWithReadLock(block: () -> T): T {
    val future: Future<T> = ApplicationManager.getApplication().executeOnPooledThread<T> { ReadAction.compute<T, Throwable> { block() } }
    PlatformTestUtil.waitWithEventsDispatching("bgt", { future.isDone }, 10)
    return future.get()
}

/** Runs [block] and returns the errors the platform logged meanwhile instead of failing the test with them. */
internal fun loggedErrorsOf(block: () -> Unit): List<String> {
    val errors = mutableListOf<String>()
    LoggedErrorProcessor.executeWith<Throwable>(object : LoggedErrorProcessor() {
        override fun processError(category: String, message: String, details: Array<out String>, t: Throwable?): Set<Action> {
            errors += "$category: $message ${t?.let { "${it.javaClass.name}: ${it.message}" }.orEmpty()}"
            return emptySet()
        }
    }) { block() }
    return errors
}
