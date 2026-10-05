package me.tbsten.katachi.intellij.presentation.rules

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

/** The "no role holds this file" notification of one file, or none (design 2.1). */
internal sealed interface NoRoleDecision {
    data object Hidden : NoRoleDecision

    data class NoRole(
        /** Nearest first, at most as many as the definition model offers. */
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
    ) : NoRoleDecision
}

/**
 * Decides the no-role notification of [input]'s file. Pure; the provider calls it in the background.
 * It is shown only when every one of these holds, in this order of cheapness:
 * - both switches are on, and the file was not closed with the notification's ×
 * - the definition has an answer (a loading or failed one still carries the last)
 * - `forFile` found a definition and the file is `Unexpected` / `UnexpectedUnder`
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
        MatchOutcome.Unexpected -> null
        is MatchOutcome.UnexpectedUnder -> outcome.directory
        MatchOutcome.Matched, is MatchOutcome.Ignored, is MatchOutcome.NotWalked, MatchOutcome.IgnoredByVcs -> return NoRoleDecision.Hidden
    }
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
    )
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
