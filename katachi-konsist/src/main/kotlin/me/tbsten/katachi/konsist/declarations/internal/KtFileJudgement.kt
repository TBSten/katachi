package me.tbsten.katachi.konsist.declarations.internal

import java.io.File
import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.FileConstraintFailure
import me.tbsten.katachi.dsl.FileConstraintSubject
import me.tbsten.katachi.konsist.KatachiKonsistNoKotlinFilesException
import me.tbsten.katachi.konsist.declarations.KatachiKtFileContentNothingWrittenException
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
 * @param declaredAt where `ktFileContent { }` was called, for the exceptions and the messages.
 * @param rulesOf the rules of one file, given project relative.
 * @throws KatachiKonsistNoKotlinFilesException when the covered files hold no `.kt` file.
 * @throws KatachiKtFileContentNothingWrittenException when the block writes no line for any file.
 */
internal fun judgeKtFiles(
    subject: FileConstraintSubject,
    declaredAt: DeclarationSite,
    rulesOf: (file: String) -> FileRules,
): List<FileConstraintFailure> {
    val kotlinFiles = subject.files.filter { it.endsWith(KOTLIN_EXTENSION) }
    // Covered files and none Konsist reads: said out loud by konsistScopeOf, as for konsist { }.
    // Nothing covered at all is a place that has not filled up yet, and says nothing.
    if (subject.files.isEmpty()) return emptyList()
    val parsed = konsistScopeOf(subject).files.associateBy { File(it.path).invariantSeparatorsPath }

    val rulesByFile = kotlinFiles.map { it to rulesOf(it) }
    if (rulesByFile.all { (_, rules) -> rules.isEmpty }) {
        throw KatachiKtFileContentNothingWrittenException(role = subject.role.qualifiedName, declaredAt = declaredAt)
    }
    return rulesByFile.flatMap { (file, rules) ->
        if (rules.isEmpty) return@flatMap emptyList()
        val absolute = "${subject.projectRoot}/$file"
        val koFile = parsed[absolute] ?: return@flatMap emptyList()
        val facts = subject.memo(KtFileFactsKey(absolute), KtFileFacts::class) { ktFileFactsOf(koFile, file) }
        judgeKtFile(facts, rules).map { failureOf(it, file, rules) }
    }
}

/** What is wrong with one file, in the order the report shows it. */
internal fun judgeKtFile(facts: KtFileFacts, rules: FileRules): List<KtFileFinding> {
    val packageFinding = judgePackage(rules.packages, facts)
    val imports = judgeImports(rules.imports, facts.imports)
    val declarations = rules.declarations?.let { judgeDeclarations(it, facts.declarations) }
    val located = listOfNotNull(packageFinding?.takeIf { it.hasLine() }) + imports.located + declarations?.located.orEmpty()
    val lineless = listOfNotNull(packageFinding?.takeUnless { it.hasLine() }) + imports.missing + declarations?.missing.orEmpty()
    return located + lineless
}

/** Whether the report places [this] at a line of the file: a missing package has none. */
private fun KtFileFinding.hasLine(): Boolean = when (this) {
    is UnexpectedPackage -> packageName != null
    else -> true
}

