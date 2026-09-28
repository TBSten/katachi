package me.tbsten.katachi.intellij.presentation

/**
 * Every text of the tool window. The mapper words the UI state through this, so the Composables
 * never build a sentence.
 *
 * TODO: stage 3 backs the plugin's instance with a DynamicBundle (KatachiBundle.properties) and adds
 *  the English text; until then both the plugin and the preview use [JapaneseKatachiStrings].
 */
internal interface KatachiStrings {
    // Status screens
    val initializing: String
    val loadingTitle: String
    val loadingNote: String
    val cancel: String
    val showLog: String
    val reload: String
    val sync: String
    val details: String
    fun refreshing(since: String): String
    fun minutesAgo(minutes: Long): String
    fun hoursAgo(hours: Long): String
    fun daysAgo(days: Long): String
    val justNow: String

    // Empty states
    val noTemplatesTitle: String
    val noTemplatesBody: String
    val howToWrite: String
    val notGradleTitle: String
    val notGradleBody: String
    val notSyncedTitle: String
    val notSyncedBody: String
    val notInstalledTitle: String
    val notInstalledBody: String
    val installGuide: String
    fun outdatedTitle(version: String?): String
    val outdatedBody: String
    val updateGuide: String
    fun searchEmptyTitle(query: String): String
    val clearSearch: String

    // Load errors
    val loadErrorTitle: String
    val compileFailed: String
    val compileFailedHint: String
    val staleSync: String
    val staleSyncHint: String
    val architectureNotSet: String
    val architectureNotSetHint: String
    val openBuildScript: String
    val gradleFailed: String
    val gradleFailedHint: String
    val jsonMissing: String
    val jsonMalformed: String
    val jsonIncompatible: String
    val jsonIncompatibleHint: String
    val openJson: String
    val loadCancelled: String
    val staleListBanner: String
    val definitionChanged: String
    fun removedTemplates(names: List<String>): String

    // The list
    val searchPlaceholder: String
    val outsideSearch: String
    fun filledCount(filled: Int, total: Int): String
    val previewFailed: String
    fun unknownKinds(kinds: List<String>): String
    val unresolvedTarget: String
    val copyCommand: String
    val showCause: String
    val causeLoading: String

    // The inline form
    fun collapsedField(name: String, controller: String, value: String): String
    fun valueLabel(value: String): String
    val chooseOne: String
    val requiredError: String
    fun notAnInt(min: Int, max: Int): String
    val notAccepted: String

    /**
     * The note under a capture's field: where its value goes. [markedPattern] is the pattern with
     * the capture's `*` written `<name>`. English, for stage 3: "The directory for <name> in
     * [markedPattern]" / "The existing module for <name> in [markedPattern]".
     */
    fun capturePathHint(name: String, markedPattern: String): String
    fun captureModuleHint(name: String, markedPattern: String): String
    val captureSeparatorError: String
    val captureDotError: String
    val multilineOn: String
    val multilineOff: String
    val alreadyExists: String

    // The footer
    val nothingSelected: String
    fun reasonRequired(role: String, parameter: String): String
    fun reasonNotAnInt(role: String, parameter: String): String
    fun reasonNotAccepted(role: String, parameter: String): String
    fun reasonInvalidCapture(role: String, capture: String): String
    fun reasonUnavailable(role: String): String
    fun reasonUnresolved(role: String, fileName: String): String
    val reasonGenerating: String
    val reasonLoading: String
    val onExisting: String
    val onExistingAsk: String
    val onExistingSkip: String
    val onExistingOverwrite: String
    val overwriteWarning: String
    val generate: String

    // Generating and the result
    fun rowWritten(count: Int): String
    fun rowRunning(taskPath: String?): String
    val rowWaiting: String
    val rowAwaitingConflict: String
    fun runningTask(taskPath: String): String
    val noArguments: String
    fun generatingProgress(position: Int, total: Int): String
    val waitingForLoad: String
    val waitingForConflict: String
    val fileNew: String
    val fileOverwritten: String
    val fileSkipped: String
    val openedMarker: String
    val rowSkipped: String
    val rowStopped: String
    val rowInterrupted: String
    val rowNotRun: String
    val writesUnknown: String
    val outputIncomplete: String
    val definitionCompileFailed: String
    val generationTaskNotFound: String
    fun resultAll(count: Int): String
    fun resultPartial(done: Int, total: Int): String
    fun resultNothingWritten(total: Int): String
    fun undoHint(label: String): String
    val retryRemaining: String
    val continueGenerating: String
    val uncheckAll: String
}
