package me.tbsten.katachi.intellij.testing

import kotlinx.coroutines.CoroutineDispatcher
import kotlin.coroutines.CoroutineContext

/**
 * A single-threaded dispatcher that runs nothing by itself: the test decides which queued task runs
 * and when, so an interleaving that a real thread pool only hits by chance can be played exactly.
 *
 * ```kotlin
 * val dispatcher = ManualDispatcher()
 * val scope = CoroutineScope(dispatcher + Job())
 * scope.launch { ... }
 * dispatcher.runAll()
 * ```
 */
internal class ManualDispatcher : CoroutineDispatcher() {
    private val queue = ArrayDeque<Runnable>()

    val pending: Int get() = queue.size

    /** Runs after every task, to look at the states between two resumptions. */
    var afterTask: (() -> Unit)? = null

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        queue.addLast(block)
    }

    /** Runs queued tasks, and the tasks they queue, until nothing is left. */
    fun runAll() {
        var guard = 0
        while (queue.isNotEmpty()) {
            check(++guard < 100_000) { "ManualDispatcher: tasks keep queueing themselves" }
            queue.removeFirst().run()
            afterTask?.invoke()
        }
    }

    /** Takes every queued task out without running it, to run later with [run]. */
    fun hold(): List<Runnable> = queue.toList().also { queue.clear() }

    /** Runs [tasks] taken out by [hold], then whatever they queue. */
    fun run(tasks: List<Runnable>) {
        tasks.forEach {
            it.run()
            afterTask?.invoke()
        }
        runAll()
    }
}
