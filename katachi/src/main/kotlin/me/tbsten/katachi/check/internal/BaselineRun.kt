package me.tbsten.katachi.check.internal

import me.tbsten.katachi.check.KatachiBaselineCheckConflictException
import me.tbsten.katachi.check.KatachiBaselineNotFoundException
import me.tbsten.katachi.check.KatachiBaselineWriteException
import me.tbsten.katachi.check.MissingFile
import me.tbsten.katachi.check.Severity
import me.tbsten.katachi.check.UnexpectedFile
import me.tbsten.katachi.check.Violation
import me.tbsten.katachi.check.ViolationKind
import me.tbsten.katachi.dsl.Baseline
import me.tbsten.katachi.dsl.files.FsPath
import me.tbsten.katachi.internal.catching
import me.tbsten.katachi.internal.fileUri

/** The role name a report suggests for the baseline file, the one its documentation uses. */
internal const val BASELINE_ROLE_NAME: String = "Baseline"

/**
 * A run after the baseline had its say.
 *
 * @property violations what the run reports now: the violations the ledger did not hold back,
 *   the stale entries, and everything no check is accountable for, sorted by kind.
 * @property trailer the lines that end a failure report, or go to standard error when nothing
 *   failed: how much was held back, and how much was reported because an entry was exceeded.
 * @property notices what the run did to the file, for standard error whatever the result.
 */
internal class BaselineOutcome(
    val violations: List<Violation>,
    val trailer: List<String>,
    val notices: List<String>,
)

/**
 * Compares what [ran] found with [baseline], updating or pruning the file first when [mode]
 * says so.
 *
 * What the layout says about the baseline file itself is taken out of [ran] before anything
 * else. An `[UnexpectedFile]` about it, recorded, would hold back the one report that says the
 * file is not declared, and an update run twice would record it for good: it is reported as it
 * is instead. A `[MissingFile]` about it only arises in the update that is creating it, and is
 * dropped: recorded, it would turn stale the moment the file is written.
 *
 * @param unattributed violations no single check is accountable for (constraints nothing
 *   evaluated). They take no part in the comparison and are passed through.
 * @param declared whether a `layout { }` allows the baseline file itself; only read by an update.
 * @throws KatachiBaselineNotFoundException when the file is missing and [mode] does not create it.
 * @throws KatachiBaselineCheckConflictException when a check of [ran] found something else
 *   against the same file earlier in this JVM.
 */
internal fun applyBaseline(
    baseline: Baseline,
    projectRoot: FsPath,
    ran: List<CheckedViolations>,
    unattributed: List<Violation>,
    declared: Boolean,
    mode: BaselineMode,
    environment: BaselineEnvironment,
): BaselineOutcome {
    val root = projectRoot.value
    val file = root.trimEnd('/') + "/" + baseline.path
    val uri = fileUri(root, baseline.path)
    val isAboutItself = { violation: Violation ->
        (violation is UnexpectedFile || violation is MissingFile) && violation.path == baseline.path
    }
    val checked = ran.map { run -> CheckedViolations(run.check, run.violations.filterNot(isAboutItself)) }
    val aboutItself = ran.flatMap { run -> run.violations.filter(isAboutItself) }
        .filterIsInstance<UnexpectedFile>()
        .map { UnexpectedFile(it.path, it.nearby, suggestedRole = BASELINE_ROLE_NAME) }
    val passedThrough = unattributed + if (BASELINE_FILE_MUST_BE_DECLARED) aboutItself else emptyList()
    // A Failed warning is a note, not a hole: only a failure that fails the run makes it partial.
    val partial = (checked.flatMap { it.violations } + passedThrough)
        .any { it.kind == ViolationKind.Failed && it.severity == Severity.Error }

    if (!partial) refuseConflicts(baseline, file, uri, checked, environment.runs)

    val text = environment.store.read(file)
    if (text == null && mode != BaselineMode.Update) throw KatachiBaselineNotFoundException(uri)
    val current = text?.let { parseBaseline(it, uri) } ?: BaselineLedger(emptyMap())

    if (mode != BaselineMode.Check && partial) {
        // A path the run could not look at would lose its entry: write nothing, fail as usual.
        return BaselineOutcome(
            violations = (checked.flatMap { it.violations } + passedThrough).sortedBy { it.kind.ordinal },
            trailer = listOf("Baseline $uri was not changed, because this run could not check everything."),
            notices = emptyList(),
        )
    }

    val (ledger, notices) = when (mode) {
        BaselineMode.Check -> current to emptyList()
        BaselineMode.Update -> {
            val updated = current.updatedWith(checked)
            updated to buildList {
                add(updateSummary(uri, current, updated, checked))
                keptLine(updated, checked)?.let(::add)
                if (BASELINE_FILE_MUST_BE_DECLARED && !declared) add(undeclaredWarning(uri, baseline.path))
                if (updated.entries.isEmpty()) add(emptyNotice(uri))
            }
        }
        BaselineMode.Prune -> {
            val pruned = current.prunedWith(checked)
            pruned to buildList {
                add("Katachi baseline $uri: ${countOf(current.total - pruned.total, "fixed violation")} removed, ${countOf(pruned.total, "violation")} still held back.")
                if (pruned.entries.isEmpty()) add(emptyNotice(uri))
            }
        }
    }
    if (mode != BaselineMode.Check) {
        val rendered = renderBaseline(ledger)
        // Unchanged content is not written, so neither the file's time stamp nor git moves.
        if (rendered != text) {
            catching { environment.store.write(file, rendered) }
                .getOrElse { cause -> throw KatachiBaselineWriteException(uri, cause) }
        }
    }

    val reconciled = ledger.reconcile(checked, baseline.path)
    // A partial run cannot tell a fixed violation from one it did not see, so it calls nothing
    // stale; the report already says what could not be checked.
    val reported = if (partial) reconciled.reported - reconciled.stale.toSet() else reconciled.reported
    val violations = (reported + passedThrough).sortedBy { it.kind.ordinal }
    val trailer = buildList {
        if (mode != BaselineMode.Update) add("Baseline $uri held back ${countOf(reconciled.heldBack, "violation")}.")
        if (reconciled.exceeded > 0) {
            val exceeded = reconciled.exceeded
            add(
                if (exceeded == 1) {
                    "1 violation reported in full because its baseline entry allows fewer."
                } else {
                    "$exceeded violations reported in full because their baseline entries allow fewer."
                },
            )
        }
    }
    return BaselineOutcome(violations, trailer, notices)
}

/**
 * Throws when a check of [checked] found something other than an earlier run of it against
 * the same [file] in this JVM. Checked before anything is read or written, so the later run
 * leaves the file as it was.
 */
private fun refuseConflicts(
    baseline: Baseline,
    file: String,
    uri: String,
    checked: List<CheckedViolations>,
    runs: BaselineRuns,
) {
    val found = checked.foundCounts()
    // Grouped rather than taken run by run: one assert(...) passing a check twice is one run of it.
    for (check in checked.map { it.check }.distinct()) {
        val own = found.filterKeys { it.check == check }
        if (runs.record(file, check, own) != null) {
            throw KatachiBaselineCheckConflictException(check, uri, baseline.declaredAt)
        }
    }
}

private fun countOf(count: Int, singular: String, plural: String = "${singular}s"): String =
    if (count == 1) "1 $singular" else "$count $plural"

private fun updateSummary(
    uri: String,
    before: BaselineLedger,
    after: BaselineLedger,
    ran: List<CheckedViolations>,
): String {
    val ranChecks = ran.mapTo(HashSet()) { it.check }
    val keys = (before.entries.keys + after.entries.keys).filter { it.check in ranChecks }
    val added = keys.sumOf { maxOf(0, (after.entries[it] ?: 0) - (before.entries[it] ?: 0)) }
    val removed = keys.sumOf { maxOf(0, (before.entries[it] ?: 0) - (after.entries[it] ?: 0)) }
    val entries = after.entries.count { it.key.check in ranChecks }
    val recorded = after.entries.filterKeys { it.check in ranChecks }.values.sum()
    return "Katachi baseline $uri: ${countOf(recorded, "violation")} recorded in " +
        "${countOf(entries, "entry", "entries")} (+$added, -$removed)."
}

/** The entries an update left alone because their check did not run, or `null` when none. */
private fun keptLine(after: BaselineLedger, ran: List<CheckedViolations>): String? {
    val ranChecks = ran.mapTo(HashSet()) { it.check }
    val kept = after.entries.keys.filter { it.check !in ranChecks }
    if (kept.isEmpty()) return null
    // Named, because a check whose class was renamed leaves its old group here for good.
    return "Kept ${countOf(kept.size, "entry", "entries")} of checks that did not run: ${kept.map { it.check }.distinct().sorted().joinToString(", ")}."
}

private fun undeclaredWarning(uri: String, path: String): String =
    "Warning: no role's layout { } allows the baseline file $uri, so every run from the next one " +
        "fails with [UnexpectedFile] about it. Declare it in a role, e.g. " +
        "\"$BASELINE_ROLE_NAME\" { layout { ${layoutLineFor(path, isDirectory = false)} } }."

private fun emptyNotice(uri: String): String =
    "Baseline $uri holds back nothing any more: `baseline = ...` can be removed from the definition, " +
        "and the file deleted."
