package me.tbsten.katachi.intellij.data.script

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * The last answer of [probe], asked once and again on [recheck]. [current] never waits for the process: until the
 * first answer it is null, and [onChange] tells when an answer replaces another.
 */
internal class ScriptClasspathState(
    private val probe: () -> ScriptClasspath,
    private val inBackground: (() -> Unit) -> Unit,
    private val onChange: (before: ScriptClasspath?, after: ScriptClasspath) -> Unit,
) {
    private val answer = AtomicReference<ScriptClasspath?>(null)
    private val probing = AtomicBoolean(false)

    fun current(): ScriptClasspath? {
        if (answer.get() == null) recheck()
        return answer.get()
    }

    fun recheck() {
        if (!probing.compareAndSet(false, true)) return
        inBackground {
            val (before, after) = try {
                val answered = try {
                    probe()
                } catch (failure: RuntimeException) {
                    ScriptClasspath.Malformed("${failure::class.simpleName}: ${failure.message}")
                }
                answer.getAndSet(answered) to answered
            } finally {
                probing.set(false)
            }
            if (before != after) onChange(before, after)
        }
    }

    /** Replaces [expected] with [replacement], unless another answer came in the meantime. */
    fun replace(expected: ScriptClasspath, replacement: ScriptClasspath) {
        if (answer.compareAndSet(expected, replacement)) onChange(expected, replacement)
    }
}
