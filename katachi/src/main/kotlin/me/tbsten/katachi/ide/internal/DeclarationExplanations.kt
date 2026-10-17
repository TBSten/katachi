package me.tbsten.katachi.ide.internal

import me.tbsten.katachi.check.internal.fileConstraintSubjectOf
import me.tbsten.katachi.dsl.internal.DeclarationExplaining
import me.tbsten.katachi.dsl.internal.DeclarationExplanation
import me.tbsten.katachi.dsl.internal.DeclaredFileConstraint
import me.tbsten.katachi.dsl.internal.FileDeclarationsExplanation
import me.tbsten.katachi.dsl.internal.RuleExplanation
import me.tbsten.katachi.internal.catching
import me.tbsten.katachi.processor.internal.ProjectWalk

/**
 * The `fileDeclarations` of each constraint that explains its declarations, asked of it with the
 * subject the check hands it: the same files, capture values and per-run memo, so the answer is
 * the check's.
 *
 * At most [maxExplainedFiles] files are explained in all, in the order the constraints are
 * declared; a constraint past that budget explains a first part of its files or none, and
 * [reportTruncation] adds one `ExplanationTruncated` to [problems]. Only `.kt` files count against the
 * budget. A declaration the backend could not place at a line is left out, since the JSON's `line` is
 * an `int`. A constraint that throws gets
 * `null` and an `ExplanationFailed`, and the rest still answer.
 */
internal class DeclarationExplanations(
    private val walk: ProjectWalk,
    private val maxExplainedFiles: Int,
    private val problems: MutableList<DefinitionProblemJson>,
) {
    private var explained: Int = 0
    private var truncated: Boolean = false

    /** The `fileDeclarations` of [declared], or `null` when it does not explain its declarations or failed to. */
    fun of(declared: DeclaredFileConstraint): List<FileDeclarationsJson>? {
        val explaining = declared.check as? DeclarationExplaining ?: return null
        val files = walk.filesUnder(declared)
        // Nothing covered is not evaluated by the check either: there is nothing to explain.
        if (files.isEmpty()) return emptyList()
        val remaining = (maxExplainedFiles - explained).coerceAtLeast(0)
        // Only `.kt` files are read, and only they use the budget: any other covered file goes on
        // being handed over, since a subject without a `.kt` file would be refused as the check
        // refuses it, and a cut that dropped only those would be no cut.
        val kotlinFiles = files.filter { it.endsWith(KOTLIN_EXTENSION) }
        if (kotlinFiles.size > remaining) truncated = true
        if (kotlinFiles.isNotEmpty() && remaining == 0) return null
        val keptKotlin = kotlinFiles.take(remaining).toSet()
        val explainedFiles = files.filter { !it.endsWith(KOTLIN_EXTENSION) || it in keptKotlin }
        val subject = fileConstraintSubjectOf(walk, declared, explainedFiles)
        val answer = catching { explaining.explainDeclarations(subject) }.getOrElse { cause ->
            problems += DefinitionProblemJson(
                kind = DefinitionProblemKind.ExplanationFailed,
                message = "The constraint declared at ${declared.declaredAt} could not explain its declarations: ${oneLineOf(cause)}",
                detail = declared.role.qualifiedName,
                declaredAt = problemSiteOf(cause) ?: siteOf(declared.declaredAt),
                exception = cause.javaClass.name,
            )
            return null
        }
        explained += answer.size
        return answer.map(::fileDeclarationsJsonOf)
    }

    /** Adds the one `ExplanationTruncated` problem, when the budget cut any constraint short. */
    fun reportTruncation() {
        if (!truncated) return
        problems += DefinitionProblemJson(
            kind = DefinitionProblemKind.ExplanationTruncated,
            message = "Declarations were explained for the first $maxExplainedFiles files only; " +
                "raise --arg maxExplainedFiles= to explain more.",
            detail = null,
        )
    }
}

/** The only files a `ktFileContent { }` reads, and so the only ones the budget counts. */
private const val KOTLIN_EXTENSION: String = ".kt"

private fun fileDeclarationsJsonOf(file: FileDeclarationsExplanation): FileDeclarationsJson = FileDeclarationsJson(
    file = file.file,
    declarationsChecked = file.declarationsChecked,
    // A declaration the backend could not place has no line to show it at; the check still judges it.
    declarations = file.declarations.mapNotNull(::declarationJsonOf),
    missing = file.missing.map(::ruleJsonOf),
    unexplainedReason = file.unexplainedReason,
)

private fun declarationJsonOf(declaration: DeclarationExplanation): DeclarationJson? {
    val line = declaration.line ?: return null
    return DeclarationJson(
        kind = declaration.kind,
        name = declaration.name,
        line = line,
        verdict = declaration.verdict.name,
        rules = declaration.rules.map(::ruleJsonOf),
        difference = declaration.difference,
    )
}

private fun ruleJsonOf(rule: RuleExplanation): RuleJson = RuleJson(text = rule.text, declaredAt = siteOf(rule.declaredAt))
