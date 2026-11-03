package me.tbsten.katachi.internal

import kotlin.coroutines.cancellation.CancellationException

// KatachiInternalException is deliberately not fatal: reporting one path as UncheckedFile and checking the rest
// beats failing the run.
internal val Throwable.isFatal: Boolean
    get() = when (this) {
        is VirtualMachineError -> true
        is LinkageError -> true
        is InterruptedException -> true
        is AssertionError -> true
        else -> false
    }

// Lives in the root package because validate and evaluateTemplate, in different layers, must share this rule.
internal inline fun <T> catching(block: () -> T): Result<T> =
    runCatching(block).onFailure { if (it.isFatal) throw it }

// Unlike catching, keeps AssertionError: assertNoErrors() throws it inside this block as the processor's answer.
internal inline fun <T> runProcessorCatching(block: () -> T): Result<T> =
    runCatching(block).onFailure { if (it.endsProcessorRun) throw it }

internal val Throwable.endsProcessorRun: Boolean
    get() = when (this) {
        is AssertionError -> false
        is CancellationException -> true
        else -> isFatal
    }
