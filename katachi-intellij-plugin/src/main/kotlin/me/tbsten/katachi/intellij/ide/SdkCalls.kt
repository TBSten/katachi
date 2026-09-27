package me.tbsten.katachi.intellij.ide

import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.Logger
import java.util.concurrent.CancellationException

/**
 * Runs [block], a call into the IntelliJ Platform SDK, and returns what it threw as a failed
 * [Result] instead of letting it reach the IDE, which would show it as a red "IDE error".
 *
 * Every call the plugin makes into the SDK goes through here, so that a platform that behaves
 * differently from the one the plugin was built against (another build, Android Studio) degrades
 * one feature instead of breaking the tool window. The caller decides what the failure means to
 * the user: a feature that did not happen (Gradle did not start, a file did not open) is shown
 * through the state the screen already has, never swallowed into a success.
 *
 * What must not be caught is thrown again ([mustPropagate]): the platform's control flow
 * (`ProcessCanceledException` and the other [ControlFlowException]s) and coroutine cancellation,
 * which stop working when caught, and what the JVM cannot recover from.
 *
 * A caught failure is logged at WARN, not ERROR: `Logger.error` is what the IDE turns into the red
 * error notification (and what fails a platform test), which is exactly what this is here to
 * avoid. The stack trace still lands in idea.log.
 *
 * ```kotlin
 * val opened = sdkCall("open $file in an editor") { FileEditorManager.getInstance(project).openFile(file, false) }.isSuccess
 * ```
 *
 * @param action what the call does, for the log line: "open X in an editor".
 */
internal inline fun <T> sdkCall(action: String, block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (failure: Throwable) {
        if (failure.mustPropagate) throw failure
        SDK_CALL_LOG.warn("Could not $action", failure)
        Result.failure(failure)
    }

/**
 * Whether [sdkCall] throws this again rather than catching it: the platform's control flow
 * (`ProcessCanceledException` is a [ControlFlowException]), cancellation (coroutines' included, it
 * is the same class), and the fatal ones of docs/internal/kotlin/errors.md ("握ってはいけないもの").
 */
internal val Throwable.mustPropagate: Boolean
    get() = this is ControlFlowException ||
        this is CancellationException ||
        this is VirtualMachineError ||
        this is LinkageError ||
        this is InterruptedException ||
        this is AssertionError

internal val SDK_CALL_LOG: Logger = Logger.getInstance("#me.tbsten.katachi.intellij.ide.SdkCalls")
