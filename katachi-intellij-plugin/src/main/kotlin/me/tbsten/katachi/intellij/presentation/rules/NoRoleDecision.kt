package me.tbsten.katachi.intellij.presentation.rules

import me.tbsten.katachi.intellij.data.baseline.BaselineLedger
import me.tbsten.katachi.intellij.data.baseline.BaselineReading
import me.tbsten.katachi.intellij.model.CaptureOccurrenceModel
import me.tbsten.katachi.intellij.model.DefinitionState
import me.tbsten.katachi.intellij.model.DefinitionUnavailableReason
import me.tbsten.katachi.intellij.model.Freshness
import me.tbsten.katachi.intellij.model.MatchOutcome
import me.tbsten.katachi.intellij.model.answer

internal data class NearbyRow(
    val roleName: String,
    val directory: String,
    val site: ResolvedSiteView,
)

internal data class CaptureMismatchNote(
    val roleName: String,
    val disagreeingNames: List<String>,
    val lines: List<String>,
    val expectedPath: String?,
)

internal sealed interface BaselineVerdict {
    data class Held(val rule: String) : BaselineVerdict

    data object NotHeld : BaselineVerdict

    data object Unknown : BaselineVerdict
}

internal sealed interface NoRoleDecision {
    data object Hidden : NoRoleDecision

    data class NoRole(
        val nearby: List<NearbyRow>,
        val untracked: Boolean,
        val stale: Boolean,
        val unexpectedUnder: String?,
        val relativePath: String,
        val canOpenDefinition: Boolean,
        val captureMismatch: CaptureMismatchNote? = null,
        val baseline: BaselineVerdict = BaselineVerdict.Unknown,
    ) : NoRoleDecision
}

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
        baseline = baselineVerdictOf(input.baseline, roles.outcome, roles.relativePath, input.architecture?.baseline),
    )
}

internal fun baselineVerdictOf(reading: BaselineReading, outcome: MatchOutcome, relativePath: String, baselinePath: String?): BaselineVerdict = when (reading) {
    BaselineReading.NotConfigured -> BaselineVerdict.NotHeld
    is BaselineReading.Read ->
        if (outcome == MatchOutcome.Unexpected && relativePath == baselinePath) BaselineVerdict.NotHeld
        else reading.ledger.heldRuleOf(outcome, relativePath)?.let(BaselineVerdict::Held) ?: BaselineVerdict.NotHeld
    BaselineReading.Missing, is BaselineReading.UnknownVersion, is BaselineReading.Unreadable -> BaselineVerdict.Unknown
}

private fun BaselineLedger.heldRuleOf(outcome: MatchOutcome, relativePath: String): String? = when {
    outcome == MatchOutcome.Unexpected && holdsFile(relativePath) -> BaselineLedger.RULE_UNEXPECTED_FILE
    outcome is MatchOutcome.UnexpectedUnder && holdsDirectory(outcome.directory) -> BaselineLedger.RULE_UNEXPECTED_DIRECTORY
    outcome is MatchOutcome.CaptureMismatch && holdsCaptureMismatch(relativePath) -> BaselineLedger.RULE_CAPTURE_MISMATCH
    else -> null
}

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

private fun occurrenceText(occurrence: CaptureOccurrenceModel, from: String = ""): String {
    val expected = occurrence.expected
    val held = if (expected != null && expected != occurrence.value) " (expected \"$expected\"$from)" else ""
    return "\"${occurrence.value}\" at level ${occurrence.level + 1}$held"
}

internal fun shouldEnsureDefinitionLoaded(state: DefinitionState, loadWithoutUser: Boolean): Boolean = when (state) {
    DefinitionState.NotLoaded -> loadWithoutUser
    is DefinitionState.Unavailable -> loadWithoutUser && state.reason == DefinitionUnavailableReason.LoadingWithoutUserDisabled
    is DefinitionState.Loading, is DefinitionState.Ready -> false
}

internal fun shortDirectory(directory: String): String {
    val segments = directory.split('/').filter { it.isNotEmpty() }
    return if (segments.size <= SEGMENTS) directory else "…/" + segments.takeLast(SEGMENTS).joinToString("/")
}

private const val SEGMENTS = 2
private const val NOT_RESOLVED = "the declaration site was not resolved"
