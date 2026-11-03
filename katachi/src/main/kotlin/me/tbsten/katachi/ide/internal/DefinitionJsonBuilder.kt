package me.tbsten.katachi.ide.internal

import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.Documented
import me.tbsten.katachi.dsl.FileSelection
import me.tbsten.katachi.dsl.Group
import me.tbsten.katachi.dsl.LayoutEntry
import me.tbsten.katachi.dsl.LayoutEntryKind
import me.tbsten.katachi.dsl.Role
import me.tbsten.katachi.dsl.Summary
import me.tbsten.katachi.dsl.Title
import me.tbsten.katachi.dsl.internal.DeclaredFileConstraint
import me.tbsten.katachi.dsl.internal.ModuleIndex
import me.tbsten.katachi.dsl.internal.Template
import me.tbsten.katachi.dsl.internal.evaluateLayout
import me.tbsten.katachi.internal.catching
import me.tbsten.katachi.processor.internal.ProjectWalk
import me.tbsten.katachi.processor.internal.definitionErrorSiteOf
import me.tbsten.katachi.processor.internal.definitionErrorsOf
import me.tbsten.katachi.template.internal.TemplateDescriptionJson
import java.util.IdentityHashMap

internal class DefinitionJsonOptions(
    val maxMatchedFiles: Int,
    val explainDeclarations: Boolean,
    val maxExplainedFiles: Int,
    val katachiVersion: String,
    val generatedAt: String,
)

// A walk that cannot run does not fail the run (entries are the declared ones, problems says why); a wrong
// definition (KatachiDeclarationException) does, and then no JSON is written.
internal fun definitionJsonOf(
    walk: ProjectWalk,
    templates: TemplateDescriptionJson,
    options: DefinitionJsonOptions,
    placementsFailure: Throwable? = null,
    previewFailures: TemplatePreviewFailures = TemplatePreviewFailures.None,
): DefinitionJson {
    val architecture = walk.architecture
    val problems = mutableListOf<DefinitionProblemJson>()
    val walked = catching { WalkedLayout(walk.resolvedEntries, walk.declaredFileConstraints) }
    val layout = walked.getOrElse { cause ->
        if (definitionErrorsOf(cause).isNotEmpty()) throw cause
        problems += DefinitionProblemJson(
            kind = DefinitionProblemKind.WalkFailed,
            message = "The project could not be walked, so no layout entry lists its files: ${oneLineOf(cause)}",
            detail = null,
        )
        declaredLayoutOf(architecture, problems)
    }
    val walkSucceeded = walked.isSuccess
    if (walkSucceeded && placementsFailure != null) {
        problems += DefinitionProblemJson(
            kind = DefinitionProblemKind.ModulesNotListed,
            message = "The modules of the project could not be listed, so a template below a module capture " +
                "has no placement: ${oneLineOf(placementsFailure)}",
            detail = null,
        )
    }
    val explanations = DeclarationExplanations(walk, options.maxExplainedFiles, problems)
        .takeIf { walkSucceeded && options.explainDeclarations }
    // Every entry, the ones a module { } block injects included: `LayoutIndex.rolesOf` allows a file
    // by them, so the IDE's matching needs them to give the same answer. Each carries its `synthetic` / `place` flag.
    val entriesByRole = layout.entries.groupByIdentity { it.role }
    val constraintsByRole = layout.constraints.groupByIdentity { it.role }

    val roles = architecture.allRoles.map { role ->
        val entries = entriesByRole[role].orEmpty()
        val files = if (walkSucceeded) walk.filesOf(role) else null
        RoleJson(
            qualifiedName = role.qualifiedName,
            name = role.name,
            groupPath = role.groupPath,
            title = role[Title],
            summary = role[Summary],
            documented = role[Documented],
            declaredAt = siteOf(role.declaredAt),
            layout = entries.mapIndexed { index, entry -> layoutEntryJsonOf(index, entry, files, options.maxMatchedFiles) },
            constraints = constraintsByRole[role].orEmpty().map { declared ->
                constraintJsonOf(declared, entries, explanations?.of(declared))
            },
        )
    }
    explanations?.reportTruncation()
    templatePreviewProblemsOf(templates, previewFailures).forEach { problems += it }

    return DefinitionJson(
        schemaVersion = DEFINITION_SCHEMA_VERSION,
        katachiVersion = options.katachiVersion,
        generatedAt = options.generatedAt,
        projectRoot = if (walkSucceeded) walk.projectRootPath else null,
        architecture = architectureJsonOf(architecture),
        groups = architecture.allGroups.map(::groupJsonOf),
        roles = roles,
        templates = templates,
        problems = problems,
    )
}

private class WalkedLayout(val entries: List<LayoutEntry>, val constraints: List<DeclaredFileConstraint>)

private fun declaredLayoutOf(architecture: Architecture, problems: MutableList<DefinitionProblemJson>): WalkedLayout {
    val evaluation = catching { architecture.evaluateLayout(ModuleIndex.unresolved(architecture.moduleResolver)) }
        .getOrNull() ?: return WalkedLayout(emptyList(), emptyList())
    if (evaluation.readsModuleList) {
        problems += DefinitionProblemJson(
            kind = DefinitionProblemKind.ModulesNotListed,
            message = "A wildcard module key was left unexpanded, since the modules of the project could not be listed.",
            detail = null,
        )
    }
    return WalkedLayout(evaluation.entries, evaluation.fileConstraints)
}

private fun architectureJsonOf(architecture: Architecture): ArchitectureJson = ArchitectureJson(
    declaredAt = siteOf(architecture.declaredAt),
    files = when (val files = architecture.files) {
        FileSelection.GitTracked -> "gitTracked"
        FileSelection.WholeTree -> "wholeTree"
        else -> files::class.java.name
    },
    baseline = architecture.baseline?.path,
)

private fun groupJsonOf(group: Group): GroupJson = GroupJson(
    qualifiedName = group.qualifiedName,
    name = group.name,
    path = group.path,
    title = group[Title],
    summary = group[Summary],
    documented = group[Documented],
    declaredAt = siteOf(group.declaredAt),
)

private fun layoutEntryJsonOf(index: Int, entry: LayoutEntry, files: List<String>?, maxMatchedFiles: Int): LayoutEntryJson {
    val matched = when {
        files == null -> null
        entry.kind == LayoutEntryKind.File -> files.filter { entry.glob.matches(it) }
        entry.kind == LayoutEntryKind.AnyFile -> files.filter { file ->
            val parent = file.substringBeforeLast('/', missingDelimiterValue = "")
            parent.isNotEmpty() && entry.glob.matches(parent)
        }
        else -> null
    }
    val captures = entry.templateCaptures ?: entry.captureVariants.firstOrNull()
    val template = entry[Template]
    return LayoutEntryJson(
        index = index,
        path = entry.path,
        kind = entry.kind.name,
        required = entry.required,
        description = entry.description,
        declaredAt = siteOf(entry.declaredAt),
        modulePath = entry.modulePath,
        pathInModule = entry.pathInModule,
        captures = captures?.pathCaptures.orEmpty().map {
            PathCaptureJson(
                name = it.name,
                segmentIndex = it.segmentIndex,
                wildcardIndex = it.wildcardIndex,
                transform = it.transform?.spelling,
            )
        },
        moduleCapture = captures?.moduleCapture?.let { ModuleCaptureJson(pattern = it.modulePattern, names = it.names) },
        template = template?.let {
            TemplateRefJson(
                specifier = entry.role.qualifiedName + (it.id?.let { id -> ".$id" } ?: ""),
                id = it.id,
                title = it.title,
                declaredAt = siteOf(it.declaredAt),
            )
        },
        matchedFiles = matched.orEmpty().take(maxMatchedFiles.coerceAtLeast(0)),
        matchedCount = matched?.size,
        matchedFilesTruncated = matched != null && matched.size > maxMatchedFiles.coerceAtLeast(0),
        synthetic = entry.synthetic,
        place = entry.place,
    )
}

private fun constraintJsonOf(
    declared: DeclaredFileConstraint,
    roleEntries: List<LayoutEntry>,
    fileDeclarations: List<FileDeclarationsJson>?,
): ConstraintJson {
    val coverage = declared.coverage
    val checkClass = declared.check::class.java.name
    return ConstraintJson(
        name = declared.name,
        backend = backendOf(checkClass),
        checkClass = checkClass,
        range = declared.range.name,
        declaredAt = siteOf(declared.declaredAt),
        layoutPath = declared.layoutPath,
        anchors = declared.paths,
        entries = roleEntries.indices.filter { index ->
            val entry = roleEntries[index]
            when (entry.kind) {
                LayoutEntryKind.File -> entry.glob in coverage.fileGlobs
                LayoutEntryKind.AnyFile -> entry.glob in coverage.anyFileGlobs
                else -> false
            }
        },
        coverage = CoverageJson(
            files = coverage.fileGlobs.map { it.pattern },
            anyFileDirectories = coverage.anyFileGlobs.map { it.pattern },
        ),
        fileDeclarations = fileDeclarations,
    )
}

internal class TemplatePreviewFailures(
    val templates: Map<String, Throwable>,
    val templateSets: Map<String, Throwable>,
) {
    companion object {
        val None: TemplatePreviewFailures = TemplatePreviewFailures(emptyMap(), emptyMap())
    }
}

private fun templatePreviewProblemsOf(
    templates: TemplateDescriptionJson,
    failures: TemplatePreviewFailures,
): List<DefinitionProblemJson> {
    val previewed = templates.details.mapTo(HashSet()) { it.template }
    val previewedSets = templates.templateSetDetails.mapTo(HashSet()) { it.templateSet }
    val templateProblems = templates.templates.filterNot { it.template in previewed }.map { summary ->
        val failure = failures.templates[summary.template]
        DefinitionProblemJson(
            kind = DefinitionProblemKind.TemplatePreviewFailed,
            message = "The template could not be previewed, so it has no detail; katachiTemplates --arg template= says why.",
            detail = summary.template,
            declaredAt = failure?.let(::problemSiteOf),
            exception = failure?.javaClass?.name,
        )
    }
    val setProblems = templates.templateSets.filterNot { it.templateSet in previewedSets }.map { summary ->
        val failure = failures.templateSets[summary.templateSet]
        DefinitionProblemJson(
            kind = DefinitionProblemKind.TemplateSetPreviewFailed,
            message = "The template set could not be previewed, so it has no detail; katachiTemplates --arg template= says why.",
            detail = summary.templateSet,
            declaredAt = failure?.let(::problemSiteOf),
            exception = failure?.javaClass?.name,
        )
    }
    return templateProblems + setProblems
}

internal fun problemSiteOf(failure: Throwable): DeclarationSiteJson? =
    definitionErrorSiteOf(failure)?.let(::siteOf)

internal fun siteOf(site: DeclarationSite): DeclarationSiteJson? =
    if (site == DeclarationSite.Unknown) null else DeclarationSiteJson(fileName = site.fileName, line = site.lineNumber)

internal fun oneLineOf(cause: Throwable): String {
    val message = cause.message?.lineSequence()?.firstOrNull()?.trim().orEmpty()
    val type = cause::class.simpleName ?: cause::class.java.name
    return if (message.isEmpty()) type else "$type: $message"
}

// Role declares no equals, so group by identity.
private fun <T> List<T>.groupByIdentity(keyOf: (T) -> Role): Map<Role, List<T>> {
    val grouped = IdentityHashMap<Role, MutableList<T>>()
    for (item in this) grouped.getOrPut(keyOf(item)) { mutableListOf() } += item
    return grouped
}
