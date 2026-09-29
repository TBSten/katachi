package me.tbsten.katachi.intellij.testing

import me.tbsten.katachi.intellij.data.generate.SingleFileGenerationRequest
import me.tbsten.katachi.intellij.model.KatachiModule
import me.tbsten.katachi.intellij.model.ModuleTemplate
import me.tbsten.katachi.intellij.model.TemplateModel
import me.tbsten.katachi.intellij.presentation.entry.EditorNotificationMemory
import me.tbsten.katachi.intellij.presentation.entry.EntryAvailability
import me.tbsten.katachi.intellij.presentation.entry.EntryOrigin
import me.tbsten.katachi.intellij.presentation.entry.EntrySettings
import me.tbsten.katachi.intellij.presentation.entry.FileContentState
import me.tbsten.katachi.intellij.presentation.entry.GenerateDialogRequest
import me.tbsten.katachi.intellij.presentation.entry.LedgerEntry
import me.tbsten.katachi.intellij.presentation.entry.NotificationInput
import me.tbsten.katachi.intellij.presentation.entry.PlacementMatch
import java.nio.file.Path

// Builders for the placement, dialog and entry types (K1). Platform-free: compiled into uiTest too.

/** A match of [model] in [definition]; a file match unless [remainingPath] is given. */
internal fun placementMatch(
    model: TemplateModel = template("data.Repository"),
    definition: KatachiModule = module(),
    decided: Map<String, String> = emptyMap(),
    undecided: List<String> = emptyList(),
    remainingPath: String = "",
    targetUndecided: Boolean = false,
): PlacementMatch = PlacementMatch(ModuleTemplate(definition, model), decided, undecided, remainingPath, targetUndecided)

/** An absolute path under the fixtures' [ROOT]. */
internal fun underRoot(relative: String): Path = ROOT.resolve(relative)

internal fun editorFile(relative: String): EntryOrigin.EditorFile = EntryOrigin.EditorFile(underRoot(relative))

internal fun newMenuDirectory(relative: String): EntryOrigin.NewMenuDirectory = EntryOrigin.NewMenuDirectory(underRoot(relative))

/** The request the notification's [Create] makes for [match] at [origin]: its decided captures as seeds. */
internal fun dialogRequest(match: PlacementMatch = placementMatch(), origin: EntryOrigin = editorFile("src/Foo.kt")): GenerateDialogRequest =
    GenerateDialogRequest(origin, match.id, match.decided)

internal fun notificationInput(
    file: Path = underRoot("src/Foo.kt"),
    content: FileContentState = FileContentState.Empty,
    availability: EntryAvailability = EntryAvailability.Ready,
    matches: List<PlacementMatch> = listOf(placementMatch()),
    settings: EntrySettings = EntrySettings(),
    memory: EditorNotificationMemory = EditorNotificationMemory.EMPTY,
    ledgerEntry: LedgerEntry? = null,
    commentable: Boolean = true,
): NotificationInput = NotificationInput(file, content, availability, matches, settings, memory, ledgerEntry, commentable)

internal fun singleFileRequest(
    match: PlacementMatch = placementMatch(),
    origin: EntryOrigin = editorFile("src/Foo.kt"),
    args: List<Pair<String, String>> = match.decided.toList(),
    target: Path? = origin.path,
): SingleFileGenerationRequest = SingleFileGenerationRequest(match.template, origin, args, target)
