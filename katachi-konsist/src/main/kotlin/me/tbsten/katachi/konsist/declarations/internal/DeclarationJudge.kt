package me.tbsten.katachi.konsist.declarations.internal

import java.util.Collections
import java.util.IdentityHashMap

// Decision order matters: a `not.` line, a full allowed line, an `allow(..., anythingElse)`, otherwise wrong (mismatched or unexpected).
// A required line that nothing matches and no mismatched declaration claims is missing.

internal class DeclarationFindings(
    val located: List<KtFileFinding>,
    val missing: List<KtFileFinding>,
    val fileLevel: List<JudgedDeclaration>,
    val fileLevelMissing: List<DeclarationRule>,
)

internal class JudgedDeclaration(val fact: DeclarationFact, val outcome: DeclarationOutcome)

internal sealed interface DeclarationOutcome {
    class Prohibited(val rule: DeclarationRule) : DeclarationOutcome

    class Allowed(val rules: List<DeclarationRule>) : DeclarationOutcome

    class AllowedByAnythingElse(val rules: List<AnythingElseRule>) : DeclarationOutcome

    class Mismatched(val rule: DeclarationRule, val differences: List<String>) : DeclarationOutcome

    data object Unexpected : DeclarationOutcome
}

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

private sealed interface Decision {
    class Prohibited(val rule: DeclarationRule) : Decision

    class Allowed(val rules: List<DeclarationRule>) : Decision

    class AllowedByAnythingElse(val rules: List<AnythingElseRule>) : Decision

    class Wrong(val nameMatched: List<DeclarationRule>) : Decision
}

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
                // The declaration itself is fine; its members may not be. A line without `{ }` allows none.
                val members = fact.members
                if (members != null) {
                    val memberRules = decision.rules.firstNotNullOfOrNull { it.members }
                        ?: DeclarationListRules.membersOfBodilessLine(decision.rules.first())
                    judgeList(memberRules, members, containers + fact, located, missing)
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
        // A name that is a pattern names no one declaration, so none is "the one meant, in another shape".
        val found = if (rule.name.hasWildcard) {
            null
        } else {
            facts.withIndex().firstOrNull { (index, fact) ->
                nameMatches(rule, fact) && decisions[index].let { it is Decision.Allowed || it is Decision.AllowedByAnythingElse }
            }?.value
        }
        missing(containers.size, MissingDeclaration(rule, list, found))
    }
    return ListJudgement(judged, unclaimed)
}

private fun decide(rules: DeclarationListRules, allowedLines: List<DeclarationRule>, fact: DeclarationFact): Decision {
    rules.lines.firstOrNull { it.prohibited && fullMatch(it, fact) }?.let { return Decision.Prohibited(it) }
    allowedLines.filter { fullMatch(it, fact) }.takeIf { it.isNotEmpty() }?.let { return Decision.Allowed(it) }
    rules.anythingElse.filter { anythingElseMatches(it, fact) }.takeIf { it.isNotEmpty() }
        ?.let { return Decision.AllowedByAnythingElse(it) }
    // A line with `*` in its name matches names it was never written for, so a declaration it does not fully match is not
    // "the one this line meant": it is an extra one, not a wrong version of it. A choice such as {A,B} still names its
    // declarations one by one, and stays a candidate.
    return Decision.Wrong(allowedLines.filter { !it.name.hasStar && nameMatches(it, fact) })
}

// Compares by identity: a line is the same line only as itself.
private fun <T> identitySetOf(items: List<T>): Set<T> =
    Collections.newSetFromMap(IdentityHashMap<T, Boolean>()).apply { addAll(items) }
