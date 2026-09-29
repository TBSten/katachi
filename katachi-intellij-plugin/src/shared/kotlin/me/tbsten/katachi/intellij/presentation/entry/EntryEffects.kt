package me.tbsten.katachi.intellij.presentation.entry

import me.tbsten.katachi.intellij.model.TemplateId
import java.nio.file.Path

/** Where the user started from: the file of an editor notification, or the directory of the New menu. */
internal sealed interface EntryOrigin {
    /** Absolute. */
    val path: Path

    /** The file an editor notification sits on. Every capture is decided by it (decision 21). */
    data class EditorFile(override val path: Path) : EntryOrigin

    /**
     * The directory the New menu was opened on; with several selected, the first one the chosen
     * template fits (decision 23).
     */
    data class NewMenuDirectory(override val path: Path) : EntryOrigin
}

/** What the generate dialog opens with. The dialog lists every template (issue 0), [initialTemplate] selected. */
internal data class GenerateDialogRequest(
    val origin: EntryOrigin,
    /** The first template of the index order for a notification (decision 9), the picked leaf for the New menu. */
    val initialTemplate: TemplateId,
    /** [initialTemplate]'s captures decided by [origin]: the initial values of its fields, editable (issue 1). */
    val seeds: Map<String, String>,
)

/**
 * What the editor notification and the New menu ask of the IDE beyond generating (which is
 * `IdeEffects`). One port per effect, one implementer per effect: `ide/entry/EntryEffectsImpl`.
 *
 * Called on the EDT (a link of the notification panel, `actionPerformed`). Implementations run
 * their SDK calls through `sdkCall`: a failure tells the user in a balloon instead of failing the
 * click, and control flow (`ProcessCanceledException`, cancellation) is thrown again.
 *
 * ```kotlin
 * createLink.doClick() // -> entryEffects.openGenerateDialog(GenerateDialogRequest(EntryOrigin.EditorFile(file), first.id, first.decided))
 * ```
 */
internal interface EntryEffects {
    /** [Create]: opens the generate dialog; its [Generate] hands over to `SingleFileGeneration` (D4). */
    fun openGenerateDialog(request: GenerateDialogRequest)

    /** ⚙: opens the katachi page of the settings; no focus on an item, there is no public API (issue 14, D4). */
    fun openSettings()

    /** [View template]: shows the tool window with [template] selected and highlighted (C2). */
    fun revealTemplateInToolWindow(template: TemplateId)
}
