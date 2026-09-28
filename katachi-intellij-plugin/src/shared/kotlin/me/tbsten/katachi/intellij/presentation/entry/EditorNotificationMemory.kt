package me.tbsten.katachi.intellij.presentation.entry

import java.nio.file.Path

/**
 * What the editor notification remembers per file for the project session (issue 15): the files
 * whose notification was closed with ×, and those whose "has content" notification was already
 * shown once. Memory only, never persisted: a new project service starts from [EMPTY].
 *
 * Immutable: every change returns a new memory, so a decision reads one consistent value and the
 * sequence tests can compare states. The project service wrapper (E1) holds the current one in an
 * atomic reference, written from the EDT (×) and from the background (the first showing).
 * A file katachi just generated counts as closed (decision 8): E1 dismisses it when the ledger says so.
 *
 * ```kotlin
 * val next = memory.dismiss(file)
 * check(next.isDismissed(file) && !next.isDismissed(other))
 * ```
 */
internal class EditorNotificationMemory {
    // TODO(A3): the per-file sets and the four operations. Until then it remembers nothing.

    fun isDismissed(file: Path): Boolean = false

    /** Whether the "has content" notification of [file] was shown once already ("only the first time"). */
    fun hasShownContentNotice(file: Path): Boolean = false

    fun dismiss(file: Path): EditorNotificationMemory = this

    fun markContentNoticeShown(file: Path): EditorNotificationMemory = this

    companion object {
        val EMPTY: EditorNotificationMemory = EditorNotificationMemory()
    }
}
