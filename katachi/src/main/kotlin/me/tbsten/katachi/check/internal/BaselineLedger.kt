package me.tbsten.katachi.check.internal

import me.tbsten.katachi.check.StaleBaselineEntry
import me.tbsten.katachi.check.Violation

/**
 * The contents of a baseline file: how many violations of each key are held back.
 *
 * A count rather than a set, because two violations can share a key — two classes of the same
 * name in one file, two unnamed constraints on one role — and a set would let the third one
 * through unnoticed.
 */
internal class BaselineLedger(val entries: Map<BaselineKey, Int>) {
    /** The checks this ledger holds entries for, in the order the file lists them. */
    val checks: Set<String> get() = entries.keys.mapTo(sortedSetOf()) { it.check }

    /** How many violations the ledger holds back in all. */
    val total: Int get() = entries.values.sum()

    override fun equals(other: Any?): Boolean = other is BaselineLedger && other.entries == entries
    override fun hashCode(): Int = entries.hashCode()
    override fun toString(): String = "BaselineLedger($entries)"
}

/** What one check reported in a run, filed under the check's fully qualified class name. */
internal class CheckedViolations(val check: String, val violations: List<Violation>)

/**
 * A run compared with the ledger.
 *
 * @property reported what still fails the run or is reported as a warning: every violation the
 *   ledger did not hold back, in the order the checks gave them, followed by [stale].
 * @property heldBack how many violations the ledger held back.
 * @property exceeded how many of [reported] are there only because their key turned up more
 *   often than the ledger allows, so every one of them is reported.
 */
internal class Reconciliation(
    val reported: List<Violation>,
    val heldBack: Int,
    val exceeded: Int,
    val stale: List<StaleBaselineEntry>,
)

/** How often each key turns up in these runs; a violation the baseline never holds back has none. */
internal fun List<CheckedViolations>.foundCounts(): Map<BaselineKey, Int> =
    flatMap { run -> run.violations.mapNotNull { baselineKeyOf(run.check, it) } }.groupingBy { it }.eachCount()

/**
 * Compares [ran] with this ledger. Only the entries of the checks in [ran] take part: a check
 * that did not run says nothing about whether its entries still hold.
 *
 * A key found no more often than the ledger allows is held back entirely. A key found more often
 * is reported in full, since which of its violations is the new one cannot be told. An entry
 * found less often than it allows is stale by the difference.
 *
 * @param baselinePath the baseline file, relative to the project root, for the stale entries to
 *   name.
 */
internal fun BaselineLedger.reconcile(ran: List<CheckedViolations>, baselinePath: String): Reconciliation {
    val found = ran.foundCounts()
    val unheld = ran.flatMap { run ->
        run.violations.filter { violation ->
            val key = baselineKeyOf(run.check, violation) ?: return@filter true
            found.getValue(key) > (entries[key] ?: 0)
        }
    }
    val heldBack = found.entries.sumOf { (key, count) -> if (count <= (entries[key] ?: 0)) count else 0 }
    val exceeded = found.entries.sumOf { (key, count) -> if ((entries[key] ?: 0) in 1 until count) count else 0 }
    val ranChecks = ran.mapTo(HashSet()) { it.check }
    val stale = entries.entries
        .filter { (key, allowed) -> key.check in ranChecks && (found[key] ?: 0) < allowed }
        .sortedBy { it.key }
        .map { (key, allowed) -> StaleBaselineEntry(key, allowed, found[key] ?: 0, baselinePath) }
    return Reconciliation(unheld + stale, heldBack, exceeded, stale)
}

/**
 * This ledger with the entries of every check in [ran] replaced by what those checks found now.
 * The entries of a check that did not run are kept as they are.
 */
internal fun BaselineLedger.updatedWith(ran: List<CheckedViolations>): BaselineLedger {
    val ranChecks = ran.mapTo(HashSet()) { it.check }
    return BaselineLedger(entries.filterKeys { it.check !in ranChecks } + ran.foundCounts())
}

/**
 * This ledger with every entry of a check in [ran] lowered to what was found now, and dropped
 * when nothing was. Never raises a count and never adds an entry: a new violation still fails.
 */
internal fun BaselineLedger.prunedWith(ran: List<CheckedViolations>): BaselineLedger {
    val ranChecks = ran.mapTo(HashSet()) { it.check }
    val found = ran.foundCounts()
    return BaselineLedger(
        entries
            .mapValues { (key, allowed) -> if (key.check in ranChecks) minOf(allowed, found[key] ?: 0) else allowed }
            .filterValues { it > 0 },
    )
}
