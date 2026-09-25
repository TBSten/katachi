package me.tbsten.katachi.util.internal

import me.tbsten.katachi.util.KatachiMultipleFailuresException
import me.tbsten.katachi.util.RunCatchingScopedScope

@PublishedApi
internal interface RunCatchingScopedController : RunCatchingScopedScope {
    fun throwIfHasFailures()

    class Threw(val exception: Throwable) : Throwable()
}

@PublishedApi
internal fun newRunCatchingScopedScope(): RunCatchingScopedController = RunCatchingScopedScopeImpl()

private class RunCatchingScopedScopeImpl : RunCatchingScopedController {
    private val failures = mutableListOf<Throwable>()

    override fun `throw`(exception: Throwable): Nothing {
        throw RunCatchingScopedController.Threw(exception)
    }

    override fun failure(exception: Throwable) {
        failures.add(exception)
    }

    override fun throwIfHasFailures() {
        if (failures.isNotEmpty()) {
            throw KatachiMultipleFailuresException(failures.toList())
        }
    }
}
