@file:OptIn(InternalKatachiApi::class)

package me.tbsten.katachi.konsist.declarations.internal

import me.tbsten.katachi.InternalKatachiApi
import me.tbsten.katachi.dsl.FileConstraintSubject
import me.tbsten.katachi.dsl.internal.DeclarationExplanation
import me.tbsten.katachi.dsl.internal.DeclarationVerdict
import me.tbsten.katachi.dsl.internal.FileDeclarationsExplanation
import me.tbsten.katachi.dsl.internal.RuleExplanation

/*
 * The IDE's explanation of a file: how each top-level declaration was decided, by which lines.
 * It reads the same rules, the same facts and the same `judgeDeclarations` the check does -- only
 * what it keeps differs -- so a declaration it calls allowed is one the check lets be.
 */

/**
 * [subject]'s `.kt` files explained, in order. A file whose block throws is still listed, with the
 * reason; what fails for every file at once ([readKtFiles]'s exceptions) is thrown.
 *
 * @param rulesOf the rules of one file, given project relative.
 */
internal fun explainKtFiles(
    subject: FileConstraintSubject,
    rulesOf: (file: String) -> FileRules,
): List<FileDeclarationsExplanation> =
    readKtFiles(subject) { file -> runCatchingPerFile { rulesOf(file) } }.map { input ->
        val rules = input.rules.getOrElse { cause -> return@map unexplained(input.file, reasonOf(cause)) }
        // A block that wrote no line about declarations judges none, so Konsist's reading of the
        // file does not matter, and the check does not make its facts for it either.
        if (rules.declarations == null) return@map notChecked(input.file)
        val facts = input.facts ?: return@map unexplained(input.file, "Konsist did not read this file.")
        explainKtFile(input.file, facts, rules)
    }

/** One file explained against [rules]: its own declarations, decided as `judgeKtFile` decides them. */
internal fun explainKtFile(file: String, facts: KtFileFacts, rules: FileRules): FileDeclarationsExplanation {
    val declarationRules = rules.declarations
        ?: return notChecked(file)
    val findings = judgeDeclarations(declarationRules, facts.declarations)
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

/** A file whose rules wrote no line about its declarations: nothing binds them, and nothing is wrong with it. */
private fun notChecked(file: String): FileDeclarationsExplanation =
    FileDeclarationsExplanation(file, declarationsChecked = false, emptyList(), emptyList(), unexplainedReason = null)

private fun unexplained(file: String, reason: String): FileDeclarationsExplanation =
    FileDeclarationsExplanation(file, declarationsChecked = false, emptyList(), emptyList(), unexplainedReason = reason)

/** The type of [cause] and the first line of its message. */
private fun reasonOf(cause: Throwable): String {
    val message = cause.message?.lineSequence()?.firstOrNull()?.trim().orEmpty()
    val type = cause::class.simpleName ?: cause::class.java.name
    return if (message.isEmpty()) type else "$type: $message"
}

/**
 * [block]'s answer, or what it threw -- except what has to end the run: the JVM giving up, a
 * broken classpath, an interrupt.
 */
private inline fun <T> runCatchingPerFile(block: () -> T): Result<T> =
    runCatching(block).onFailure { cause ->
        if (cause is VirtualMachineError || cause is LinkageError || cause is InterruptedException) throw cause
    }
