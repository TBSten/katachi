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
    /**
     * How each declaration directly in the file was decided, in line order: what [located] and
     * [missing] say about the file's own declarations, before it is worded. The IDE's explanation
     * of a file reads this, so it can never decide a declaration otherwise than the check.
     */
    val fileLevel: List<JudgedDeclaration>,
    /** The required lines of the file's own list that nothing matched, in the order written. */
    val fileLevelMissing: List<DeclarationRule>,
)

/** One declaration of a list and how it was decided. */
internal class JudgedDeclaration(val fact: DeclarationFact, val outcome: DeclarationOutcome)

/** How one declaration was decided, with the lines that decided it. */
internal sealed interface DeclarationOutcome {
    /** A `not.` line matches it in full. */
    class Prohibited(val rule: DeclarationRule) : DeclarationOutcome

    /** These allowed lines match it in full, in the order written. */
    class Allowed(val rules: List<DeclarationRule>) : DeclarationOutcome

    /** No line matches it, and these `anythingElse()` do. */
    class AllowedByAnythingElse(val rules: List<AnythingElseRule>) : DeclarationOutcome

    /** An allowed line matches its name, [rule] being the one it is reported against. */
    class Mismatched(val rule: DeclarationRule, val differences: List<String>) : DeclarationOutcome

    /** Nothing matches it, not even by name. */
    data object Unexpected : DeclarationOutcome
}

/** Judges [facts], the declarations of a file, against [rules]. */
internal fun judgeDeclarations(rules: DeclarationListRules, facts: List<DeclarationFact>): DeclarationFindings {
    val located = mutableListOf<KtFileFinding>()
    val missingByDepth = sortedMapOf<Int, MutableList<KtFileFinding>>()
    val fileLevel = judgeList(rules, facts, containers = emptyList(), located) { depth, finding ->
        missingByDepth.getOrPut(depth) { mutableListOf() } += finding
    }
    return DeclarationFindings(
        located = located,
        missing = missingByDepth.values.flatten(),
        fileLevel = fileLevel.judged,
        fileLevelMissing = fileLevel.missing,
    )
}

/** How one declaration was decided, before a mismatch is told from an unexpected one. */
private sealed interface Decision {
    class Prohibited(val rule: DeclarationRule) : Decision

    class Allowed(val rules: List<DeclarationRule>) : Decision

    class AllowedByAnythingElse(val rules: List<AnythingElseRule>) : Decision

    class Wrong(val nameMatched: List<DeclarationRule>) : Decision
}

/** What one list was decided as: each of its declarations, and its required lines nothing matched. */
private class ListJudgement(val judged: List<JudgedDeclaration>, val missing: List<DeclarationRule>)

private fun judgeList(
    rules: DeclarationListRules,
    facts: List<DeclarationFact>,
    containers: List<DeclarationFact>,
    located: MutableList<KtFileFinding>,
    missing: (depth: Int, KtFileFinding) -> Unit,
): ListJudgement {
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

    val judged = facts.mapIndexed { index, fact ->
        val outcome = when (val decision = decisions[index]) {
            is Decision.Prohibited -> {
                located += ProhibitedDeclaration(fact, decision.rule, list)
                DeclarationOutcome.Prohibited(decision.rule)
            }
            is Decision.Allowed -> {
                // The declaration itself is fine; its members may not be.
                val withBody = decision.rules.firstOrNull { it.members != null }
                val members = fact.members
                if (withBody?.members != null && members != null) {
                    judgeList(withBody.members, members, containers + fact, located, missing)
                }
                DeclarationOutcome.Allowed(decision.rules)
            }
            is Decision.AllowedByAnythingElse -> DeclarationOutcome.AllowedByAnythingElse(decision.rules)
            is Decision.Wrong -> {
                val rule = claims[index]
                if (rule == null) {
                    located += UnexpectedDeclaration(fact, list, unclaimed)
                    DeclarationOutcome.Unexpected
                } else {
                    val differences = differencesOf(rule, fact)
                    located += MismatchedDeclaration(fact, rule, list, differences)
                    DeclarationOutcome.Mismatched(rule, differences)
                }
            }
        }
        JudgedDeclaration(fact, outcome)
    }

    for (rule in unclaimed) {
        val found = facts.withIndex().firstOrNull { (index, fact) ->
            nameMatches(rule, fact) && decisions[index].let { it is Decision.Allowed || it is Decision.AllowedByAnythingElse }
        }?.value
        missing(containers.size, MissingDeclaration(rule, list, found))
    }
    return ListJudgement(judged, unclaimed)
}

private fun decide(rules: DeclarationListRules, allowedLines: List<DeclarationRule>, fact: DeclarationFact): Decision {
    rules.lines.firstOrNull { it.prohibited && fullMatch(it, fact) }?.let { return Decision.Prohibited(it) }
    allowedLines.filter { fullMatch(it, fact) }.takeIf { it.isNotEmpty() }?.let { return Decision.Allowed(it) }
    rules.anythingElse.filter { anythingElseMatches(it, fact) }.takeIf { it.isNotEmpty() }
        ?.let { return Decision.AllowedByAnythingElse(it) }
    return Decision.Wrong(allowedLines.filter { nameMatches(it, fact) })
}

/** A set that compares by identity: a line is the same line only as itself. */
private fun <T> identitySetOf(items: List<T>): Set<T> =
    Collections.newSetFromMap(IdentityHashMap<T, Boolean>()).apply { addAll(items) }
