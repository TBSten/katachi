package me.tbsten.katachi.intellij.presentation.rules

import me.tbsten.katachi.intellij.model.CaptureOccurrenceModel
import me.tbsten.katachi.intellij.model.DefinitionState
import me.tbsten.katachi.intellij.model.DefinitionUnavailableReason
import me.tbsten.katachi.intellij.model.Freshness
import me.tbsten.katachi.intellij.model.MatchOutcome
import me.tbsten.katachi.intellij.model.answer

/** One place a file could move to, with where its role is declared (design 2.3). */
internal data class NearbyRow(
    val roleName: String,
    /** Relative to the project root. */
    val directory: String,
    val site: ResolvedSiteView,
)

/**
 * A file a pattern of [roleName] matched but held back because its bound captures do not agree (katachi's
 * `[CaptureMismatch]`, design capture-check 3.8): the names that disagree, what each occurrence read, and the one path
 * the file belongs at ([expectedPath]; `null` when katachi could not tell). The notification says this instead of nearby roles.
 */
internal data class CaptureMismatchNote(
    val roleName: String,
    /** `capture("feature")`, as the report writes the names whose levels disagree. */
    val disagreeingNames: List<String>,
    /** One per bound name (plain occurrences) and per derived occurrence, as the report's lines after the first: `feature: "home" at level 2, "settings" at level 8 (expected "home")`. */
    val lines: List<String>,
    val expectedPath: String?,
)

/** The "no role holds this file" notification of one file, or none (design 2.1). */
internal sealed interface NoRoleDecision {
    data object Hidden : NoRoleDecision

    data class NoRole(
        /** Nearest first, at most as many as the definition model offers. Empty for a [captureMismatch]. */
        val nearby: List<NearbyRow>,
        /** `gitTracked()` does not see the file yet: the wording says "once you add it to git". */
        val untracked: Boolean,
        /** The answer is from before a change or a failed reload. */
        val stale: Boolean,
        /** The first directory above the file that no role declared, for the tooltip. */
        val unexpectedUnder: String?,
        /** The path of the file relative to the project root, for the draft of a role. */
        val relativePath: String,
        /** `architecture { }` can be opened. */
        val canOpenDefinition: Boolean,
        /** The file is held back by the captures of a pattern that matched it, not unknown to every role. */
        val captureMismatch: CaptureMismatchNote? = null,
    ) : NoRoleDecision
}

/**
 * Decides the no-role notification of [input]'s file. Pure; the provider calls it in the background.
 * It is shown only when every one of these holds, in this order of cheapness:
 * - both switches are on, and the file was not closed with the notification's ×
 * - the definition has an answer (a loading or failed one still carries the last)
 * - `forFile` found a definition and the file is `Unexpected` / `UnexpectedUnder` / `CaptureMismatch`
 *
 * ```kotlin
 * when (val decision = decideNoRoleNotification(input)) {
 *     NoRoleDecision.Hidden -> Unit
 *     is NoRoleDecision.NoRole -> show(decision.nearby)
 * }
 * ```
 */
internal fun decideNoRoleNotification(input: NoRoleInput): NoRoleDecision {
    val settings = input.settings
    if (!settings.notificationsEnabled || !settings.notifyOnNoRole) return NoRoleDecision.Hidden
    if (input.memory.isNoRoleDismissed(input.file)) return NoRoleDecision.Hidden
    if (input.definition.answer == null) return NoRoleDecision.Hidden
    val roles = input.roles ?: return NoRoleDecision.Hidden
    val unexpectedUnder = when (val outcome = roles.outcome) {
        MatchOutcome.Unexpected, is MatchOutcome.CaptureMismatch -> null
        is MatchOutcome.UnexpectedUnder -> outcome.directory
        MatchOutcome.Matched, is MatchOutcome.Ignored, is MatchOutcome.NotWalked, MatchOutcome.IgnoredByVcs -> return NoRoleDecision.Hidden
    }
    val captureMismatch = (roles.outcome as? MatchOutcome.CaptureMismatch)?.let(::captureMismatchNoteOf)
    return NoRoleDecision.NoRole(
        nearby = roles.nearby.mapIndexed { index, nearby ->
            NearbyRow(nearby.role.qualifiedName, nearby.directory, input.nearbySites.getOrNull(index) ?: ResolvedSiteView.Unresolved(NOT_RESOLVED))
        },
        untracked = input.vcs == VcsTracking.Untracked && input.architecture?.isGitTracked == true,
        // An Unavailable state keeps the answer it had, but says nothing new about the file.
        stale = roles.freshness is Freshness.Stale || input.definition is DefinitionState.Unavailable,
        unexpectedUnder = unexpectedUnder,
        relativePath = roles.relativePath,
        canOpenDefinition = input.architectureSite is ResolvedSiteView.Found,
        captureMismatch = captureMismatch,
    )
}

/** [outcome] worded as katachi's `[CaptureMismatch]` block words it (the first line, then one line per name). */
internal fun captureMismatchNoteOf(outcome: MatchOutcome.CaptureMismatch): CaptureMismatchNote {
    val failed = outcome.occurrences.filter { it.expected != null && it.expected != it.value }
    val lines = buildList {
        for ((name, plain) in outcome.occurrences.filter { it.transform == null }.groupBy { it.name }) {
            add("$name: " + plain.joinToString(", ") { occurrenceText(it) })
        }
        for (derived in outcome.occurrences.filter { it.transform != null }) {
            val source = outcome.occurrences.firstOrNull { it.name == derived.name && it.transform == null && it.expected == null }
            val from = source?.let { ", from ${it.name} = \"${it.value}\"" }.orEmpty()
            add("${derived.name}.${derived.transform}: " + occurrenceText(derived, from))
        }
    }
    return CaptureMismatchNote(
        roleName = outcome.role.qualifiedName,
        disagreeingNames = failed.map { it.name }.distinct().map { "capture(\"$it\")" },
        lines = lines,
        expectedPath = outcome.expectedPath,
    )
}

/** `"home" at level 2`, plus ` (expected "settings"<from>)` when the level was held to something else. 1-based, as katachi counts levels. */
private fun occurrenceText(occurrence: CaptureOccurrenceModel, from: String = ""): String {
    val expected = occurrence.expected
    val held = if (expected != null && expected != occurrence.value) " (expected \"$expected\"$from)" else ""
    return "\"${occurrence.value}\" at level ${occurrence.level + 1}$held"
}

/** Whether a reader of [state] asks the service to load: nothing was asked yet, or loading without the user was off and is on again. */
internal fun shouldEnsureDefinitionLoaded(state: DefinitionState, loadWithoutUser: Boolean): Boolean = when (state) {
    DefinitionState.NotLoaded -> loadWithoutUser
    is DefinitionState.Unavailable -> loadWithoutUser && state.reason == DefinitionUnavailableReason.LoadingWithoutUserDisabled
    is DefinitionState.Loading, is DefinitionState.Ready -> false
}

/** The last [SEGMENTS] segments of [directory] after `…/`, as the notification's second line shows it. */
internal fun shortDirectory(directory: String): String {
    val segments = directory.split('/').filter { it.isNotEmpty() }
    return if (segments.size <= SEGMENTS) directory else "…/" + segments.takeLast(SEGMENTS).joinToString("/")
}

private const val SEGMENTS = 2
private const val NOT_RESOLVED = "the declaration site was not resolved"
