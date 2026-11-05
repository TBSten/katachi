package me.tbsten.katachi.intellij.data.gradle

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

internal class GradleActivity {
    private val lock = Any()
    private var own = 0
    private val external = HashSet<Any>()
    private val mutableBusy = MutableStateFlow(false)

    val busy: StateFlow<Boolean> = mutableBusy.asStateFlow()

    fun track(runner: GradleTaskRunner): GradleTaskRunner = object : GradleTaskRunner {
        override suspend fun run(request: GradleRunRequest, listener: GradleRunListener): GradleRunOutcome =
            counting { runner.run(request, listener) }
    }

    suspend fun <T> counting(block: suspend () -> T): T {
        change { own++ }
        try {
            return block()
        } finally {
            change { own = (own - 1).coerceAtLeast(0) }
        }
    }

    fun externalStarted(id: Any) {
        change { external += id }
    }

    fun externalEnded(id: Any) {
        change { external -= id }
    }

    suspend fun awaitIdle() {
        busy.first { !it }
    }

    private inline fun change(block: () -> Unit) = synchronized(lock) {
        block()
        mutableBusy.value = own > 0 || external.isNotEmpty()
    }
}
