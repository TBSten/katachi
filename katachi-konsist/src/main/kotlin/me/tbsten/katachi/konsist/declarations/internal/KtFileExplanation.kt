@file:OptIn(InternalKatachiApi::class)

package me.tbsten.katachi.konsist.declarations.internal

import me.tbsten.katachi.InternalKatachiApi
import me.tbsten.katachi.dsl.FileConstraintSubject
import me.tbsten.katachi.dsl.internal.DeclarationExplanation
import me.tbsten.katachi.dsl.internal.DeclarationVerdict
import me.tbsten.katachi.dsl.internal.FileDeclarationsExplanation
import me.tbsten.katachi.dsl.internal.RuleExplanation

internal fun explainKtFiles(
    subject: FileConstraintSubject,
    rulesOf: (file: String) -> FileRules,
): List<FileDeclarationsExplanation> =
    readKtFiles(subject) { file -> runCatchingPerFile { rulesOf(file) } }.map { input ->
        val rules = input.rules.getOrElse { cause -> return@map unexplained(input.file, reasonOf(cause)) }
        // A block that wrote no declaration line still judges the declarations: it allows none.
        val facts = input.facts ?: return@map unexplained(input.file, "Konsist did not read this file.")
        explainKtFile(input.file, facts, rules)
    }

internal fun explainKtFile(file: String, facts: KtFileFacts, rules: FileRules): FileDeclarationsExplanation {
    val findings = judgeDeclarations(rules.declarations, facts.declarations)
    return FileDeclarationsExplanation(
        file = file,
        declarationsChecked = true,
        declarations = findings.fileLevel.map(::explanationOf),
        missing = findings.fileLevelMissing.map(::ruleExplanationOf),
        unexplainedReason = null,
    )
}

private fun explanationOf(judged: JudgedDeclaration): DeclarationExplanation {
    val (verdict, rules, difference) = when (val outcome = judged.outcome) {
        is DeclarationOutcome.Allowed -> Triple(DeclarationVerdict.Allowed, outcome.rules.map(::ruleExplanationOf), null)
        is DeclarationOutcome.AllowedByAnythingElse ->
            Triple(DeclarationVerdict.AllowedByAnythingElse, outcome.rules.map(::ruleExplanationOf), null)
        is DeclarationOutcome.Prohibited -> Triple(DeclarationVerdict.Prohibited, listOf(ruleExplanationOf(outcome.rule)), null)
        is DeclarationOutcome.Mismatched ->
            Triple(DeclarationVerdict.Mismatched, listOf(ruleExplanationOf(outcome.rule)), outcome.differences.joinToString("; "))
        DeclarationOutcome.Unexpected -> Triple(DeclarationVerdict.Unexpected, emptyList(), null)
    }
    val fact = judged.fact
    return DeclarationExplanation(
        kind = fact.kind.keyword,
        name = fact.name,
        line = fact.line,
        verdict = verdict,
        rules = rules,
        difference = difference,
    )
}

private fun ruleExplanationOf(rule: DeclarationRule): RuleExplanation = RuleExplanation(renderRule(rule), rule.declaredAt)

private fun ruleExplanationOf(rule: AnythingElseRule): RuleExplanation = RuleExplanation(renderAnythingElse(rule), rule.declaredAt)

private fun unexplained(file: String, reason: String): FileDeclarationsExplanation =
    FileDeclarationsExplanation(file, declarationsChecked = false, emptyList(), emptyList(), unexplainedReason = reason)

private fun reasonOf(cause: Throwable): String {
    val message = cause.message?.lineSequence()?.firstOrNull()?.trim().orEmpty()
    val type = cause::class.simpleName ?: cause::class.java.name
    return if (message.isEmpty()) type else "$type: $message"
}

// Rethrows what has to end the run: the JVM giving up, a broken classpath, an interrupt.
private inline fun <T> runCatchingPerFile(block: () -> T): Result<T> =
    runCatching(block).onFailure { cause ->
        if (cause is VirtualMachineError || cause is LinkageError || cause is InterruptedException) throw cause
    }
