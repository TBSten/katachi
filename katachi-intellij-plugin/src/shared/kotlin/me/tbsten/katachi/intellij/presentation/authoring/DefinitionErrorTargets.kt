package me.tbsten.katachi.intellij.presentation.authoring

import me.tbsten.katachi.intellij.model.DeclarationSiteRef
import me.tbsten.katachi.intellij.model.DefinitionErrors
import me.tbsten.katachi.intellij.model.DefinitionProblem
import me.tbsten.katachi.intellij.model.DefinitionProblemKind
import me.tbsten.katachi.intellij.model.DefinitionSnapshot
import me.tbsten.katachi.intellij.model.ModuleId
import me.tbsten.katachi.intellij.presentation.gutter.AnnotationSeverity

/** Where a [DefinitionMark] comes from. */
internal enum class DefinitionMarkSource {
    /** A mistake in `definition-errors.json`: the definition could not be read. */
    Error,

    /** An entry of `problems[]`: the definition was read, but one template or set will fail when it is used. */
    Problem,
}

/** One line of a definition file that is underlined, and how (design 2.4). [errorIndex] is the position in `DefinitionErrors.errors`, `null` for a [DefinitionMarkSource.Problem]. */
internal data class DefinitionMark(
    val line: Int,
    val severity: AnnotationSeverity,
    val hover: String,
    val source: DefinitionMarkSource,
    val module: ModuleId,
    val errorIndex: Int?,
)

/** What the editor notification says (design 2.4): the first line of the first mistake, how many more, whether it is from before a change, and the mistakes "Go to the error" may open. */
internal data class ErrorNotificationModel(
    val firstLine: String,
    val more: Int,
    val stale: Boolean,
    /**
     * The positions in `DefinitionErrors.errors` of the mistakes that have a site, in order. "Go to the error" opens the first of
     * them whose site the test sources hold; the model cannot know which that is.
     */
    val targetCandidates: List<Int>,
)

/**
 * Which lines of a definition file katachi's mistakes and problems underline, and in which colour (design 2.4, D-7). Pure:
 * `DefinitionErrorAnnotator` finds, for the file it draws, which site is in it and hands that in as [marksOf]'s `lineOf`.
 *
 * - A mistake is an error; once the definition changed after it was read it is a weak warning starting with
 *   [AuthoringStrings.stalePrefix] (`(stale) `). The rest of the hover is katachi's own sentence.
 * - A problem of a template or a template set is a warning (a weak warning while the definition is stale). A
 *   `TemplatePreviewFailed` without a `declaredAt` goes to the line of the entry whose `.template { }` it names; a
 *   `TemplateSetPreviewFailed` without one is not drawn (the set's own line is not in the JSON).
 * - A site that is not in the file, or whose file has no such line, draws nothing: the notification still names it.
 *
 * ```kotlin
 * val marks = DefinitionErrorTargets.marksOf(errors, snapshots, definitionStale = false, lineOf = { module, site -> lineIn(file, module, site) }, lineCount = document.lineCount, strings)
 * ```
 */
internal object DefinitionErrorTargets {
    fun marksOf(
        errors: DefinitionErrors,
        definitions: List<DefinitionSnapshot>,
        definitionStale: Boolean,
        lineOf: (ModuleId, DeclarationSiteRef) -> Int?,
        lineCount: Int,
        strings: AuthoringStrings,
    ): List<DefinitionMark> {
        fun placed(line: Int?): Int? = line?.takeIf { it >= 1 && lineCount >= 1 }?.coerceAtMost(lineCount)
        val fromErrors = errors.errors.mapIndexedNotNull { index, error ->
            val site = error.site ?: return@mapIndexedNotNull null
            val line = placed(lineOf(error.module, site)) ?: return@mapIndexedNotNull null
            DefinitionMark(
                line = line,
                severity = if (errors.stale) AnnotationSeverity.WeakWarning else AnnotationSeverity.Error,
                hover = (if (errors.stale) strings.stalePrefix else "") + "katachi: " + error.message,
                source = DefinitionMarkSource.Error,
                module = error.module,
                errorIndex = index,
            )
        }
        val fromProblems = definitions.flatMap { snapshot ->
            val definition = snapshot.definition ?: return@flatMap emptyList()
            definition.problems.mapNotNull { problem ->
                if (!isDrawn(problem)) return@mapNotNull null
                val site = siteOfProblem(problem, snapshot) ?: return@mapNotNull null
                val line = placed(lineOf(snapshot.module.id, site)) ?: return@mapNotNull null
                DefinitionMark(
                    line = line,
                    severity = if (definitionStale) AnnotationSeverity.WeakWarning else AnnotationSeverity.Warning,
                    hover = (if (definitionStale) strings.stalePrefix else "") + hoverOfProblem(problem),
                    source = DefinitionMarkSource.Problem,
                    module = snapshot.module.id,
                    errorIndex = null,
                )
            }
        }
        return fromErrors + fromProblems
    }

    /** Whether the editor underlines [problem]: only the three kinds that name a template, a set or a file's declarations. */
    fun isDrawn(problem: DefinitionProblem): Boolean = problem.kind in DRAWN_KINDS

    /**
     * Where [problem] was written: its own `declaredAt`; for a `TemplatePreviewFailed` without one, the `.template { }` of
     * the layout entry [DefinitionProblem.detail] names (by its complete specifier or its id); else `null`.
     */
    fun siteOfProblem(problem: DefinitionProblem, snapshot: DefinitionSnapshot): DeclarationSiteRef? {
        problem.declaredAt?.let { return it }
        if (problem.kind != DefinitionProblemKind.TemplatePreviewFailed) return null
        val name = problem.detail?.takeIf { it.isNotBlank() } ?: return null
        return snapshot.definition?.roles.orEmpty().asSequence()
            .flatMap { it.layout.asSequence() }
            .mapNotNull { it.template }
            .firstOrNull { it.specifier == name || it.id == name }
            ?.declaredAt
    }

    /** The hover of a problem: its kind, katachi's sentence and which template or set it is. */
    fun hoverOfProblem(problem: DefinitionProblem): String {
        val head = "katachi: [${problem.kindName}] ${problem.message}"
        return problem.detail?.takeIf { it.isNotBlank() }?.let { "$head\n$it" } ?: head
    }

    /** The notification of [errors]; `null` when there is no mistake. */
    fun notificationOf(errors: DefinitionErrors): ErrorNotificationModel? {
        val first = errors.errors.firstOrNull() ?: return null
        return ErrorNotificationModel(
            firstLine = first.message.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty(),
            more = errors.errors.size - 1,
            stale = errors.stale,
            targetCandidates = errors.errors.indices.filter { errors.errors[it].site != null },
        )
    }

    private val DRAWN_KINDS = setOf(
        DefinitionProblemKind.TemplatePreviewFailed,
        DefinitionProblemKind.TemplateSetPreviewFailed,
        DefinitionProblemKind.ExplanationFailed,
    )
}
