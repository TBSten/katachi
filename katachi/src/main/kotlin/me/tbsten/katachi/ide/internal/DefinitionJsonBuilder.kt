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
import me.tbsten.katachi.template.internal.TemplateDescriptionJson
import java.util.IdentityHashMap

/** What [definitionJsonOf] is told besides the walk: the arguments of the run and when it ran. */
internal class DefinitionJsonOptions(
    val maxMatchedFiles: Int,
    val explainDeclarations: Boolean,
    val maxExplainedFiles: Int,
    val katachiVersion: String,
    val generatedAt: String,
)

/**
 * The whole of `definition.json` for one run: the declarations, what the walk found for each entry,
 * and [templates] as `templateDescription.json` holds them.
 *
 * A walk that cannot run (no project root, no git) does not fail the run: the entries are then the
 * declared ones, nothing knows its files, and `problems` says why. The IDE still learns every role.
 *
 * [placementsFailure] is why the module placements of [templates] could not be read, when they
 * could not: the templates are then listed without them. A walk that failed too already says why
 * in `problems`, so it adds nothing there.
 */
internal fun definitionJsonOf(
    walk: ProjectWalk,
    templates: TemplateDescriptionJson,
    options: DefinitionJsonOptions,
    placementsFailure: Throwable? = null,
): DefinitionJson {
    val architecture = walk.architecture
    val problems = mutableListOf<DefinitionProblemJson>()
    val walked = catching { WalkedLayout(walk.resolvedEntries, walk.declaredFileConstraints) }
    val layout = walked.getOrElse { cause ->
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
    templatePreviewProblemsOf(templates).forEach { problems += it }

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

/** The entries and constraints the JSON describes: the walk's, or the declared ones without it. */
private class WalkedLayout(val entries: List<LayoutEntry>, val constraints: List<DeclaredFileConstraint>)

/**
 * The layout as declared, for a run whose walk failed: wildcard module keys left unexpanded, which
 * `problems` says when there are any. A definition that cannot even be evaluated describes no
 * entry at all rather than failing the run.
 */
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

/**
 * One entry, with the files of its role it matched: a file entry by its pattern, an `anyFile()`
 * directory by the file's parent. [files] is `null` when the walk did not run.
 */
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

/**
 * One constraint. The entries it covers are the role's entries whose pattern is one of its
 * coverage's patterns, which for a `DirectOnly` constraint are only the entries directly in its
 * block.
 */
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

/** One problem per template that is listed but has no detail, since its preview failed. */
private fun templatePreviewProblemsOf(templates: TemplateDescriptionJson): List<DefinitionProblemJson> {
    val previewed = templates.details.mapTo(HashSet()) { it.template }
    return templates.templates.filterNot { it.template in previewed }.map { summary ->
        DefinitionProblemJson(
            kind = DefinitionProblemKind.TemplatePreviewFailed,
            message = "The template could not be previewed, so it has no detail; katachiTemplates --arg template= says why.",
            detail = summary.template,
        )
    }
}

/** [site] as the JSON writes it: `null` for a site that could not be captured. */
internal fun siteOf(site: DeclarationSite): DeclarationSiteJson? =
    if (site == DeclarationSite.Unknown) null else DeclarationSiteJson(fileName = site.fileName, line = site.lineNumber)

/** The type of [cause] and the first line of its message, for a one-line problem. */
internal fun oneLineOf(cause: Throwable): String {
    val message = cause.message?.lineSequence()?.firstOrNull()?.trim().orEmpty()
    val type = cause::class.simpleName ?: cause::class.java.name
    return if (message.isEmpty()) type else "$type: $message"
}

/** [this] grouped by the key [keyOf] gives, compared by identity: a `Role` declares no `equals`. */
private fun <T> List<T>.groupByIdentity(keyOf: (T) -> Role): Map<Role, List<T>> {
    val grouped = IdentityHashMap<Role, MutableList<T>>()
    for (item in this) grouped.getOrPut(keyOf(item)) { mutableListOf() } += item
    return grouped
}
