package me.tbsten.katachi.intellij.presentation

import me.tbsten.katachi.intellij.model.ModuleId
import me.tbsten.katachi.intellij.model.TemplateId
import java.nio.file.Path

/** What the user (or the IDE) asks the tool window to do. */
internal sealed interface KatachiIntent {
    /** The tool window was shown. Only the first one detects and loads (spec 04). */
    data object Opened : KatachiIntent

    /** ⟳. Ignored while generating (E-41) or while a load is running. */
    data object Reload : KatachiIntent

    /** ■ while loading. */
    data object CancelLoad : KatachiIntent

    /**
     * The New menu or an editor notification needs the list (issues 11, 18): detect and load once,
     * as [Opened] does, without the tool window. Nothing when loaded or loading. With loading without
     * the user turned off, only the synced data and the cached JSON are read (decision 16).
     * Sent through `KatachiProjectService.ensureLoaded()`, which hops to the EDT.
     */
    data object EnsureLoaded : KatachiIntent

    /**
     * [View template] of an editor notification: select and highlight [templateId], unfold its group
     * and module, and scroll its row into view (C2). TODO(C2)
     */
    data class RevealTemplate(val templateId: TemplateId) : KatachiIntent

    /** A Gradle sync finished: detect again when nothing was found before (E-03). */
    data object SyncCompleted : KatachiIntent

    /** A definition module's `src/` or build script changed on disk (E-44): the "reload" banner. */
    data object DefinitionChanged : KatachiIntent

    data class ToggleCheck(val templateId: TemplateId) : KatachiIntent

    data class SetExpanded(val templateId: TemplateId, val expanded: Boolean) : KatachiIntent

    data class Input(val field: FieldId, val value: String) : KatachiIntent

    /** The faint unlink icon was pressed (E-14). */
    data class Relink(val field: FieldId) : KatachiIntent

    data class Search(val query: String) : KatachiIntent

    data class SetOnExisting(val choice: OnExistingChoice) : KatachiIntent

    data object Generate : KatachiIntent

    data object CancelGeneration : KatachiIntent

    /** Result screen buttons (E-38). */
    data object RetryRemaining : KatachiIntent

    data object ContinueGenerating : KatachiIntent

    data object UncheckAll : KatachiIntent

    /** The "is gone" message timed out. */
    data object DismissRemovedTemplates : KatachiIntent

    data object DismissLoadError : KatachiIntent

    // Parts of the screen that only open or close (see ViewState).

    data class ToggleModule(val moduleId: ModuleId) : KatachiIntent

    data class ToggleDetails(val key: DetailsKey) : KatachiIntent

    /** ▸ next to a String field: one line ⇄ several lines. */
    data class ToggleMultiline(val field: FieldId) : KatachiIntent

    data object DismissDefinitionChanged : KatachiIntent

    /**
     * The disabled-reason line was pressed: open [field]'s row, even outside the search (E-13, E-16).
     * The UI moves the focus itself.
     */
    data class RevealField(val field: FieldId) : KatachiIntent

    // Handed to the IDE (IdeIntentHandler).

    /** Brings the Build / Run tool window of the latest run to the front. */
    data object ShowLog : KatachiIntent

    data object SyncGradle : KatachiIntent

    data class OpenDocs(val page: DocsPage) : KatachiIntent

    /** Opens the definition module's `build.gradle.kts` (`architecture` not set, E-34). */
    data object OpenBuildScript : KatachiIntent

    data class OpenFile(val path: Path) : KatachiIntent

    /** Runs `katachiTemplates --arg template=X` for a row whose preview failed (E-07). */
    data class ShowCause(val templateId: TemplateId) : KatachiIntent

    data class CopyCommand(val templateId: TemplateId) : KatachiIntent
}

/** Documentation pages the empty states link to. */
internal enum class DocsPage { WritingTemplates, Install, Update }
