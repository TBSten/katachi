package me.tbsten.katachi.template.internal

import me.tbsten.katachi.check.internal.moduleIndex
import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.LayoutEntry
import me.tbsten.katachi.dsl.Role
import me.tbsten.katachi.dsl.files.internal.findProjectRoot
import me.tbsten.katachi.dsl.internal.LayoutCaptures
import me.tbsten.katachi.dsl.internal.ModuleIndex
import me.tbsten.katachi.dsl.internal.ModuleMiss
import me.tbsten.katachi.dsl.internal.Template
import me.tbsten.katachi.dsl.internal.evaluateTemplate
import me.tbsten.katachi.dsl.internal.flattenLayout
import me.tbsten.katachi.processor.ArchitectureProcessContext
import me.tbsten.katachi.processor.internal.fileSystem
import me.tbsten.katachi.template.KatachiDuplicateTemplateOutputException
import me.tbsten.katachi.template.KatachiTemplateEntryNotResolvedException
import me.tbsten.katachi.template.KatachiTemplateModuleNotFoundException
import me.tbsten.katachi.template.KatachiTemplatePathMismatchException
import me.tbsten.katachi.template.KatachiTemplatePathOutsideProjectException

/**
 * What running the templates [specifiers] name, with [values], would put in the project: path to
 * contents.
 *
 * A pure function from declarations and values to a map, exactly as documentation generation is.
 * Everything that can be wrong about the output -- an unknown or ambiguous specifier, a capture
 * with no value, a value the file system refuses, two templates writing the same path -- is
 * decided here and can be pinned by a spec comparing two maps. [writeTemplateFiles] adds the one
 * thing a spec cannot compare.
 *
 * Paths are relative to the **project root**, `/` separated, which is what a `layout { }`
 * declares. The file system is read only when [values] fill in a module capture: which modules
 * exist is what such a value is checked against, and [modules] is asked for them then, once --
 * after every problem the declarations alone can show has been reported.
 */
internal fun templateFilesFor(
    context: ArchitectureProcessContext<*>,
    specifiers: List<String>,
    values: Map<String, String>,
    modules: () -> ModuleIndex = {
        moduleIndex(context.fileSystem, findProjectRoot(context.fileSystem).path, context.architecture.moduleResolver)
    },
): Map<String, String> {
    val requested = requireValidSpecifiers(specifiers)
    val table = declaredTemplatesOf(context.declaredEntries)
    val chosen = requested.map { resolveTemplate(it, table) }
    requireNoDuplicateTemplates(requested, chosen)
    requireNoConflicts(chosen, values)

    val files = linkedMapOf<String, String>()
    val ownerOf = linkedMapOf<String, DeclaredTemplate>()
    for (template in chosen) {
        val (path, content) = templateFileFor(context, template, values, modules)
        ownerOf[path]?.let { owner ->
            throw KatachiDuplicateTemplateOutputException(
                path = path,
                templates = listOf(owner.specifier, template.specifier).sorted(),
            )
        }
        files[path] = content
        ownerOf[path] = template
    }
    return files
}

/** What running [template] with [values] would produce: its resolved path, and its content. */
private fun templateFileFor(
    context: ArchitectureProcessContext<*>,
    template: DeclaredTemplate,
    values: Map<String, String>,
    modules: () -> ModuleIndex,
): Pair<String, String> {
    val role = template.role
    val entries = template.entries
    requireValidCaptureValues(role, entries, values)

    val existingValues = existingCaptureValues(
        entries = entries,
        fileSystem = context.fileSystem,
        projectRoot = { findProjectRoot(context.fileSystem).path },
        modules = modules,
    )

    // Step 2 of the design draft's section 4: narrow to the one entry this run's values pick,
    // resolving a module capture against the modules that really exist.
    val entry = resolvedEntryFor(role, template, values, modules, existingValues)

    // Step 3: evaluate. The block's return value is the whole of the file's content.
    val captureNames = captureNamesOf(entries)
    val content = evaluateTemplate(
        template = template.template,
        roleName = role.qualifiedName,
        values = values,
        captureNames = captureNames,
        isPreview = false,
        onMissingCaptures = { names, declaredAt, cause ->
            throw missingCaptureException(role, entries, names, declaredAt, fileName = null, existingValues, cause)
        },
    )

    // Step 4: fill the path's captures in, directory and file name (partial matches included).
    // `entry.templateCaptures`, not `.captureVariants.firstOrNull()`: two declarations of this
    // path may have folded into one entry, and only `templateCaptures` is guaranteed to be the
    // one `.template { }` was actually attached to -- see `LayoutEntry.templateCaptures`.
    val variant = entry.templateCaptures ?: entry.captureVariants.firstOrNull() ?: LayoutCaptures.NONE
    val missingPathCaptures = variant.pathCaptures.map { it.name }.filterNot { it in values }
    if (missingPathCaptures.isNotEmpty()) {
        throw missingCaptureException(role, entries, missingPathCaptures, template.declaredAt, fileName = null, existingValues)
    }

    // Step 5: check the values, both on their own and once filled into the whole segment.
    requireValidFilledSegments(role, entry, variant, values)

    val path = fillCapturedPath(entry.path, variant, values)

    // Step 6: the filled path has to match the very pattern it was filled from -- structurally
    // certain once every capture has a valid value, so a mismatch here is katachi's own bug.
    if (!entry.glob.matches(path)) {
        throw KatachiTemplatePathMismatchException(template = template.specifier, pattern = entry.path, path = path)
    }

    // Step 7: never write outside the project.
    requireInsideProject(role, path, template.declaredAt)

    return path to content
}

/**
 * The one [me.tbsten.katachi.dsl.LayoutEntry] [template]'s declaration names, with a module
 * capture's wildcard resolved against the modules [modules] lists.
 *
 * [template.entries][DeclaredTemplate.entries] already answers this when the declaration names no
 * module capture -- the common case. A module capture is a *kind* of module until a run's values
 * pick one that exists, so such a template is re-flattened against the real project only then, and
 * only when every name the capture needs already has a value.
 */
private fun resolvedEntryFor(
    role: Role,
    template: DeclaredTemplate,
    values: Map<String, String>,
    modules: () -> ModuleIndex,
    existingValues: (String) -> List<String>,
): LayoutEntry {
    val moduleCaptureNames = moduleCaptureNamesOf(template.entries)
    if (moduleCaptureNames.isEmpty()) return template.entries.first()

    val missing = moduleCaptureNames.filterNot { it in values }
    if (missing.isNotEmpty()) {
        throw missingCaptureException(role, template.entries, missing, template.declaredAt, fileName = null, existingValues)
    }

    val misses = mutableListOf<ModuleMiss>()
    // By `declarationKey`, not `==`: `LayoutTemplate.equals` only holds within one flatten, and
    // this is a second one -- the same declaration comes back as instances it would never equal.
    val key = template.template.declarationKey
    val resolved = role.flattenLayout(modules().boundTo(values, misses))
        .filter { it.role === role && it[Template]?.declarationKey == key }
    misses.firstOrNull()?.let { throw moduleNotFoundFor(role, it, template) }
    // Never fall back to `template.entries`: an un-resolved entry still holds what
    // `wildcard(name)` read while no module was bound (`<name>`), and would write that text into
    // the path as a directory of its own. `resolved` is empty only when the values matched no
    // module, which `misses` above already reported.
    return resolved.firstOrNull() ?: throw KatachiTemplateEntryNotResolvedException(
        template = template.specifier,
        values = moduleCaptureNames.associateWith { values.getValue(it) },
        declaredAt = template.declaredAt,
    )
}

private fun moduleNotFoundFor(role: Role, miss: ModuleMiss, template: DeclaredTemplate): KatachiTemplateModuleNotFoundException =
    KatachiTemplateModuleNotFoundException(
        role = role.qualifiedName,
        fileName = template.entries.first().path.substringAfterLast('/'),
        modulePattern = miss.modulePattern,
        captureNames = miss.captureNames,
        modulePath = miss.modulePath,
        existing = miss.existing,
        existingArgs = miss.existingValues.map { modulesValues ->
            miss.captureNames.zip(modulesValues).joinToString(" ") { (name, value) -> "--arg $name=$value" }
        },
        declaredAt = template.declaredAt,
    )

/**
 * Refuses a resolved path that leaves the project.
 *
 * [me.tbsten.katachi.dsl.LayoutEntry.path] is documented as relative to the project root, so
 * reaching this means a `layout { }` block named a directory that climbs out. Checked rather than
 * trusted because what is about to be written is the user's own source tree: everything else
 * katachi writes lands below `build/`, and that net is gone here.
 */
private fun requireInsideProject(role: Role, path: String, declaredAt: DeclarationSite) {
    val climbs = path.startsWith('/') || path.split('/').any { it == ".." }
    if (!climbs) return
    throw KatachiTemplatePathOutsideProjectException(role = role.qualifiedName, path = path, declaredAt = declaredAt)
}

/**
 * [this] as a text file's contents: ending in a newline, the way every other file in the tree does.
 *
 * Deliberately unused by [templateFilesFor]: the design draft's section 1 keeps `.template { }`'s
 * return value exactly as written, with no trailing newline added, so that "Kotlin's own string
 * templates" stays literally true. Kept for a caller that still wants the old behaviour.
 */
internal fun String.withFinalNewline(): String =
    if (isEmpty() || endsWith("\n")) this else this + "\n"
