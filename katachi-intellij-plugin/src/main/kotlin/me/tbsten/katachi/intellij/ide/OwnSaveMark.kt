package me.tbsten.katachi.intellij.ide

import java.util.concurrent.atomic.AtomicInteger

/**
 * Whether this plugin is saving the editors right now (design 4.8). A load, a generation and a reload for an entry
 * save the editors before Gradle reads the sources, and the VFS events of those saves reach the file watcher inside
 * the very call (the platform delivers them synchronously, in the write action of the save). They are not the
 * user's changes: they must neither mark the definition stale nor schedule another reload, which would run the same
 * Gradle build again and then, saving again, chain.
 *
 * `IdeEffectsImpl` wraps only the save itself, on the EDT, in [around], so that a save the user makes while the
 * request waits for the EDT is not mistaken for it. The file watcher asks [isSaving].
 *
 * ```kotlin
 * val mark = OwnSaveMark()
 * mark.around { FileDocumentManager.getInstance().saveAllDocuments() }
 * if (mark.isSaving) return // a VFS event of a save the plugin made
 * ```
 */
internal class OwnSaveMark {
    private val saving = AtomicInteger(0)

    val isSaving: Boolean get() = saving.get() > 0

    /** Runs [block] with [isSaving] true. */
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
