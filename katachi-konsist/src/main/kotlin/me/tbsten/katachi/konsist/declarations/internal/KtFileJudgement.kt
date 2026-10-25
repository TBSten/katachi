package me.tbsten.katachi.konsist.declarations.internal

import java.io.File
import me.tbsten.katachi.dsl.FileConstraintFailure
import me.tbsten.katachi.dsl.FileConstraintSubject
import me.tbsten.katachi.konsist.KatachiKonsistNoKotlinFilesException
import me.tbsten.katachi.konsist.internal.konsistScopeOf

/** The only files Konsist reads, and so the only files a `ktFileContent { }` is about. */
private const val KOTLIN_EXTENSION: String = ".kt"

/** One file's facts within a run, shared by every `ktFileContent { }` that covers it. */
private data class KtFileFactsKey(val absolutePath: String)

/**
 * Reads [subject]'s `.kt` files in turn and judges each against the rules [rulesOf] builds for it,
 * returning what does not match: per file, the package, then the imports, then the declarations
 * and their members in line order, and what is missing last.
 *
 * The files are parsed the way `konsist { }` parses them (`konsistScopeOf`), so a file both read
 * in one run is parsed once.
 *
 * @param rulesOf the rules of one file, given project relative.
 * @throws KatachiKonsistNoKotlinFilesException when the covered files hold no `.kt` file.
 */
internal fun judgeKtFiles(
    subject: FileConstraintSubject,
    rulesOf: (file: String) -> FileRules,
): List<FileConstraintFailure> =
    readKtFiles(subject) { file -> Result.success(rulesOf(file)) }.flatMap { input ->
        val rules = input.rules.getOrThrow()
        val facts = input.facts
        if (facts == null) return@flatMap emptyList()
        judgeKtFile(facts, rules).map { failureOf(it, input.file, rules) }
    }

/** One `.kt` file of a subject: the rules the block wrote for it, and its facts when Konsist read it. */
internal class KtFileInput(
    /** Project relative. */
    val file: String,
    /** The rules of this file, or what the block threw while writing them. */
    val rules: Result<FileRules>,
    factsOf: () -> KtFileFacts?,
) {
    /** What Konsist read off the file, written down on first use; `null` when Konsist did not read it. */
    val facts: KtFileFacts? by lazy(LazyThreadSafetyMode.NONE, factsOf)
}

/**
 * The `.kt` files of [subject], each with its rules and its facts: what [judgeKtFiles] judges, and
 * what the IDE's explanation decides the same way. Empty when [subject] covers nothing.
 *
 * [rulesOf] decides whether a block that throws ends the whole run (the check) or only that file's
 * answer (the explanation). A file the block writes no line for stays in the list, and is judged
 * like any other: with nothing allowed, whatever it holds is wrong.
 *
 * @throws KatachiKonsistNoKotlinFilesException when the covered files hold no `.kt` file.
 */
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

/** What is wrong with one file, in the order the report shows it. */
internal fun judgeKtFile(facts: KtFileFacts, rules: FileRules): List<KtFileFinding> {
    val packageFinding = judgePackage(rules.packages, facts)
    val imports = judgeImports(rules.imports, facts.imports)
    val declarations = judgeDeclarations(rules.declarations, facts.declarations)
    val located = listOfNotNull(packageFinding?.takeIf { it.hasLine() }) + imports + declarations.located
    val lineless = listOfNotNull(packageFinding?.takeUnless { it.hasLine() }) + declarations.missing
    return located + lineless
}

/** Whether the report places [this] at a line of the file: a missing package has none. */
private fun KtFileFinding.hasLine(): Boolean = when (this) {
    is UnexpectedPackage -> packageName != null
    else -> true
}

