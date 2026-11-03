package me.tbsten.katachi.intellij.ide

import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.Logger
import java.util.concurrent.CancellationException

// Rethrows what must not be caught (`mustPropagate`: control flow, cancellation, fatal errors).
// Failures are logged at WARN, not ERROR: `Logger.error` becomes the red IDE error notification.
internal inline fun <T> sdkCall(action: String, block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (failure: Throwable) {
        if (failure.mustPropagate) throw failure
        SDK_CALL_LOG.warn("Could not $action", failure)
        Result.failure(failure)
    }

internal val Throwable.mustPropagate: Boolean
    get() = this is ControlFlowException ||
        this is CancellationException ||
        this is VirtualMachineError ||
        this is LinkageError ||
        this is InterruptedException ||
        this is AssertionError

internal val SDK_CALL_LOG: Logger = Logger.getInstance("#me.tbsten.katachi.intellij.ide.SdkCalls")
