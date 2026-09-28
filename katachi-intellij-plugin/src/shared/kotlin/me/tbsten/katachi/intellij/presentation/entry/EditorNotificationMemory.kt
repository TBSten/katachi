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
internal class EditorNotificationMemory private constructor(
    private val dismissed: Set<Path>,
    private val contentNoticeShown: Set<Path>,
) {
    fun isDismissed(file: Path): Boolean = file in dismissed

    /** Whether the "has content" notification of [file] was shown once already ("only the first time"). */
    fun hasShownContentNotice(file: Path): Boolean = file in contentNoticeShown

    // plusElement, not plus: a Path is an Iterable<Path>, so `set + path` would add its name segments.
    fun dismiss(file: Path): EditorNotificationMemory =
        if (isDismissed(file)) this else EditorNotificationMemory(dismissed.plusElement(file), contentNoticeShown)

    fun markContentNoticeShown(file: Path): EditorNotificationMemory =
        if (hasShownContentNotice(file)) this else EditorNotificationMemory(dismissed, contentNoticeShown.plusElement(file))

    override fun equals(other: Any?): Boolean =
        other is EditorNotificationMemory && dismissed == other.dismissed && contentNoticeShown == other.contentNoticeShown

    override fun hashCode(): Int = 31 * dismissed.hashCode() + contentNoticeShown.hashCode()

    override fun toString(): String = "EditorNotificationMemory(dismissed=$dismissed, contentNoticeShown=$contentNoticeShown)"

    companion object {
        val EMPTY: EditorNotificationMemory = EditorNotificationMemory(emptySet(), emptySet())
    }
}
