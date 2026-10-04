package me.tbsten.katachi.konsist.declarations.internal

import java.util.Collections
import java.util.IdentityHashMap

/*
 * Judging the declarations of a file, and the members of the classes whose lines have a `{ }`,
 * against their allow lists. Each declaration is decided in this order:
 *
 * 1. a `not.` line matches it in full: prohibited
 * 2. an allowed line matches it in full: allowed (and its members are judged by the `{ }` of the
 *    first such line that has one)
 * 3. an `anythingElse()` matches it: allowed (its members are not looked at)
 * 4. otherwise it is wrong: mismatched when an allowed line matches its name, unexpected if not
 *
 * Then each required line that nothing matches, and that no mismatched declaration claims, is
 * missing.
 */

/** What [judgeDeclarations] found, in the order the report shows it. */
internal class DeclarationFindings(
    /** Findings about a declaration, in line order, members right after their class. */
    val located: List<KtFileFinding>,
    /** Missing lines: the file's first, then the members', each in the order written. */
    val missing: List<KtFileFinding>,
)

/** Judges [facts], the declarations of a file, against [rules]. */
internal fun judgeDeclarations(rules: DeclarationListRules, facts: List<DeclarationFact>): DeclarationFindings {
    val located = mutableListOf<KtFileFinding>()
    val missingByDepth = sortedMapOf<Int, MutableList<KtFileFinding>>()
    judgeList(rules, facts, containers = emptyList(), located) { depth, finding ->
        missingByDepth.getOrPut(depth) { mutableListOf() } += finding
    }
    return DeclarationFindings(located, missingByDepth.values.flatten())
}

/** How one declaration was decided. */
private sealed interface Decision {
    class Prohibited(val rule: DeclarationRule) : Decision

    class Allowed(val rules: List<DeclarationRule>) : Decision

    data object AllowedByAnythingElse : Decision

    class Wrong(val nameMatched: List<DeclarationRule>) : Decision
}

private fun judgeList(
    rules: DeclarationListRules,
    facts: List<DeclarationFact>,
    containers: List<DeclarationFact>,
    located: MutableList<KtFileFinding>,
    missing: (depth: Int, KtFileFinding) -> Unit,
) {
    val allowedLines = rules.lines.filterNot { it.prohibited }
    val decisions = facts.map { fact -> decide(rules, allowedLines, fact) }
    val satisfied: Set<DeclarationRule> = identitySetOf(decisions.flatMap { (it as? Decision.Allowed)?.rules.orEmpty() })
    val list = DeclarationListContext(rules, containers, satisfied)

    // A mismatched declaration claims the line its name matches -- the first one still missing,
    // or the first one at all -- so that line is not also reported missing.
    val claims = decisions.map { decision ->
        (decision as? Decision.Wrong)?.nameMatched?.let { lines -> lines.firstOrNull(list::isMissing) ?: lines.firstOrNull() }
    }
    val claimed = identitySetOf(claims.filterNotNull())
    val unclaimed = allowedLines.filter { list.isMissing(it) && it !in claimed }

    for ((index, fact) in facts.withIndex()) {
        when (val decision = decisions[index]) {
            is Decision.Prohibited -> located += ProhibitedDeclaration(fact, decision.rule, list)
            is Decision.Allowed -> {
                // The declaration itself is fine; its members may not be.
                val withBody = decision.rules.firstOrNull { it.members != null }
                val members = fact.members
                if (withBody?.members != null && members != null) {
                    judgeList(withBody.members, members, containers + fact, located, missing)
                }
            }
            Decision.AllowedByAnythingElse -> Unit
            is Decision.Wrong -> {
                val rule = claims[index]
                located += if (rule == null) {
                    UnexpectedDeclaration(fact, list, unclaimed)
                } else {
                    MismatchedDeclaration(fact, rule, list, differencesOf(rule, fact))
                }
            }
        }
    }

    for (rule in unclaimed) {
        val found = facts.withIndex().firstOrNull { (index, fact) ->
            nameMatches(rule, fact) && decisions[index].let { it is Decision.Allowed || it == Decision.AllowedByAnythingElse }
        }?.value
        missing(containers.size, MissingDeclaration(rule, list, found))
    }
}

private fun decide(rules: DeclarationListRules, allowedLines: List<DeclarationRule>, fact: DeclarationFact): Decision {
    rules.lines.firstOrNull { it.prohibited && fullMatch(it, fact) }?.let { return Decision.Prohibited(it) }
    allowedLines.filter { fullMatch(it, fact) }.takeIf { it.isNotEmpty() }?.let { return Decision.Allowed(it) }
    if (rules.anythingElse.any { anythingElseMatches(it, fact) }) return Decision.AllowedByAnythingElse
    return Decision.Wrong(allowedLines.filter { nameMatches(it, fact) })
}

/** A set that compares by identity: a line is the same line only as itself. */
private fun <T> identitySetOf(items: List<T>): Set<T> =
    Collections.newSetFromMap(IdentityHashMap<T, Boolean>()).apply { addAll(items) }
