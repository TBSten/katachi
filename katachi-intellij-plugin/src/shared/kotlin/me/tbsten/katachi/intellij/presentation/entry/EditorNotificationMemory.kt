package me.tbsten.katachi.intellij.presentation.entry

import java.nio.file.Path

/**
 * What the editor notification remembers per file for the project session (issue 15): the files
 * whose notification was closed with ×, those whose "has content" notification was already
 * shown once, and, apart from both, the files whose "no role holds this file" notification was closed
 * with its own ×. Memory only, never persisted: a new project service starts from [EMPTY].
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
    private val noRoleDismissed: Set<Path>,
) {
    fun isDismissed(file: Path): Boolean = file in dismissed

    /** Whether the "has content" notification of [file] was shown once already ("only the first time"). */
    fun hasShownContentNotice(file: Path): Boolean = file in contentNoticeShown

    /** Whether the "no role holds this file" notification of [file] was closed with × (its own ×: the template notification's does not close it). */
    fun isNoRoleDismissed(file: Path): Boolean = file in noRoleDismissed

    // plusElement, not plus: a Path is an Iterable<Path>, so `set + path` would add its name segments.
    fun dismiss(file: Path): EditorNotificationMemory =
        if (isDismissed(file)) this else EditorNotificationMemory(dismissed.plusElement(file), contentNoticeShown, noRoleDismissed)

    /**
     * Forgets that [file] was closed: a file katachi generated and the user then deleted is a new file when it appears again,
     * so its notification may show.
     */
    fun forgetDismissed(file: Path): EditorNotificationMemory =
        if (!isDismissed(file)) this else EditorNotificationMemory(dismissed - setOf(file), contentNoticeShown, noRoleDismissed)

    fun markContentNoticeShown(file: Path): EditorNotificationMemory =
        if (hasShownContentNotice(file)) this else EditorNotificationMemory(dismissed, contentNoticeShown.plusElement(file), noRoleDismissed)

    /** × on the "no role holds this file" notification of [file]. It stays closed for the session, whatever the definition becomes. */
    fun dismissNoRole(file: Path): EditorNotificationMemory =
        if (isNoRoleDismissed(file)) this else EditorNotificationMemory(dismissed, contentNoticeShown, noRoleDismissed.plusElement(file))

    override fun equals(other: Any?): Boolean =
        other is EditorNotificationMemory && dismissed == other.dismissed && contentNoticeShown == other.contentNoticeShown &&
            noRoleDismissed == other.noRoleDismissed

    override fun hashCode(): Int = 31 * (31 * dismissed.hashCode() + contentNoticeShown.hashCode()) + noRoleDismissed.hashCode()

    override fun toString(): String =
        "EditorNotificationMemory(dismissed=$dismissed, contentNoticeShown=$contentNoticeShown, noRoleDismissed=$noRoleDismissed)"

    companion object {
        val EMPTY: EditorNotificationMemory = EditorNotificationMemory(emptySet(), emptySet(), emptySet())
    }
}
