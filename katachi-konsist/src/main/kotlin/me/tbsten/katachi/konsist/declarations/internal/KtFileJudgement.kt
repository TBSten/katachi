package me.tbsten.katachi.konsist.declarations.internal

import java.io.File
import me.tbsten.katachi.dsl.FileConstraintFailure
import me.tbsten.katachi.dsl.FileConstraintSubject
import me.tbsten.katachi.konsist.KatachiKonsistNoKotlinFilesException
import me.tbsten.katachi.konsist.internal.konsistScopeOf

private const val KOTLIN_EXTENSION: String = ".kt"

private data class KtFileFactsKey(val absolutePath: String)

// Parsed the way `konsist { }` parses them (`konsistScopeOf`), so a file both read in one run is parsed once.
internal fun judgeKtFiles(
    subject: FileConstraintSubject,
    rulesOf: (file: String) -> FileRules,
): List<FileConstraintFailure> =
    readKtFiles(subject) { file -> Result.success(rulesOf(file)) }.flatMap { input ->
        val rules = input.rules.getOrThrow()
        val facts = input.facts
        if (facts == null) return@flatMap emptyList()
        failuresOf(judgeKtFile(facts, rules) + judgeActuals(rules.actuals, facts, File(subject.projectRoot)), input.file, rules)
    }

internal class KtFileInput(
    val file: String,
    val rules: Result<FileRules>,
    factsOf: () -> KtFileFacts?,
) {
    val facts: KtFileFacts? by lazy(LazyThreadSafetyMode.NONE, factsOf)
}

internal fun readKtFiles(
    subject: FileConstraintSubject,
    rulesOf: (file: String) -> Result<FileRules>,
): List<KtFileInput> {
    val kotlinFiles = subject.files.filter { it.endsWith(KOTLIN_EXTENSION) }
    // Covered files and none Konsist reads: said out loud by konsistScopeOf, as for konsist { }.
    // Nothing covered at all is a place that has not filled up yet, and says nothing.
    if (subject.files.isEmpty()) return emptyList()
    val parsed = konsistScopeOf(subject).files.associateBy { File(it.path).invariantSeparatorsPath }

    val rulesByFile = kotlinFiles.map { it to rulesOf(it) }
    return rulesByFile.map { (file, rules) ->
        val absolute = "${subject.projectRoot}/$file"
        KtFileInput(file = file, rules = rules) {
            parsed[absolute]?.let { koFile ->
                subject.memo(KtFileFactsKey(absolute), KtFileFacts::class) { ktFileFactsOf(koFile, file) }
            }
        }
    }
}

internal fun judgeKtFile(facts: KtFileFacts, rules: FileRules): List<KtFileFinding> {
    val packageFinding = judgePackage(rules.packages, facts)
    val imports = judgeImports(rules.imports, facts.imports)
    val fileAnnotations = judgeFileAnnotations(rules.fileAnnotations, facts.fileAnnotations)
    val declarations = judgeDeclarations(rules.declarations, facts.declarations)
    val located = listOfNotNull(packageFinding?.takeIf { it.hasLine() }) + fileAnnotations + imports + declarations.located
    val lineless = listOfNotNull(packageFinding?.takeUnless { it.hasLine() }) + declarations.missing
    return located + lineless
}

private fun KtFileFinding.hasLine(): Boolean = when (this) {
    is UnexpectedPackage -> packageName != null
    is ProhibitedPackage -> packageName != null
    else -> true
}

