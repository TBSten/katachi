package me.tbsten.katachi.check.internal

import me.tbsten.katachi.check.StaleBaselineEntry
import me.tbsten.katachi.check.Violation

internal class BaselineLedger(val entries: Map<BaselineKey, Int>) {
    val checks: Set<String> get() = entries.keys.mapTo(sortedSetOf()) { it.check }

    val total: Int get() = entries.values.sum()

    override fun equals(other: Any?): Boolean = other is BaselineLedger && other.entries == entries
    override fun hashCode(): Int = entries.hashCode()
    override fun toString(): String = "BaselineLedger($entries)"
}

internal class CheckedViolations(val check: String, val violations: List<Violation>)

internal class Reconciliation(
    val reported: List<Violation>,
    val heldBack: Int,
    val exceeded: Int,
    val stale: List<StaleBaselineEntry>,
)

internal fun List<CheckedViolations>.foundCounts(): Map<BaselineKey, Int> =
    flatMap { run -> run.violations.mapNotNull { baselineKeyOf(run.check, it) } }.groupingBy { it }.eachCount()

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

internal fun BaselineLedger.updatedWith(ran: List<CheckedViolations>): BaselineLedger {
    val ranChecks = ran.mapTo(HashSet()) { it.check }
    return BaselineLedger(entries.filterKeys { it.check !in ranChecks } + ran.foundCounts())
}

internal fun BaselineLedger.prunedWith(ran: List<CheckedViolations>): BaselineLedger {
    val ranChecks = ran.mapTo(HashSet()) { it.check }
    val found = ran.foundCounts()
    return BaselineLedger(
        entries
            .mapValues { (key, allowed) -> if (key.check in ranChecks) minOf(allowed, found[key] ?: 0) else allowed }
            .filterValues { it > 0 },
    )
}
