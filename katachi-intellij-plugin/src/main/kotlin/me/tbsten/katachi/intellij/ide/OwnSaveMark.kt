package me.tbsten.katachi.intellij.ide

import java.util.concurrent.atomic.AtomicInteger

// The plugin's own saves must not mark the definition stale or schedule a reload, which would chain.
internal class OwnSaveMark {
    private val saving = AtomicInteger(0)

    val isSaving: Boolean get() = saving.get() > 0

    inline fun <T> around(block: () -> T): T {
        begin()
        try {
            return block()
        } finally {
            end()
        }
    }

    @PublishedApi internal fun begin() {
        saving.incrementAndGet()
    }

    @PublishedApi internal fun end() {
        saving.decrementAndGet()
    }
}
