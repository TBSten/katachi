package me.tbsten.katachi.template.internal

import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.LayoutEntry
import me.tbsten.katachi.dsl.Role
import me.tbsten.katachi.dsl.files.internal.findProjectRoot
import me.tbsten.katachi.dsl.internal.LayoutCaptures
import me.tbsten.katachi.dsl.internal.fillCapturedPath
import me.tbsten.katachi.dsl.internal.TemplateSetDeclaration
import me.tbsten.katachi.dsl.internal.evaluateTemplate
import me.tbsten.katachi.processor.ArchitectureProcessContext
import me.tbsten.katachi.processor.internal.fileSystem
import me.tbsten.katachi.template.KatachiDuplicateTemplateOutputException
import me.tbsten.katachi.template.KatachiTemplatePathMismatchException
import me.tbsten.katachi.template.KatachiTemplatePathOutsideProjectException

internal fun templateFilesFor(
    context: ArchitectureProcessContext<*>,
    specifiers: List<String>,
    values: Map<String, String>,
): Map<String, String> = templateRunFor(context, specifiers, values).files

internal class TemplateRun(val files: Map<String, String>, val templateSet: TemplateSetDeclaration?)

internal fun templateRunFor(
    context: ArchitectureProcessContext<*>,
    specifiers: List<String>,
    values: Map<String, String>,
): TemplateRun {
    val requested = requireValidSpecifiers(specifiers)
    val table = declaredTemplatesOf(context.declaredEntries)
    val chosen = when (val target = resolveTemplateTarget(requested, table, context.architecture.allTemplateSets)) {
        is TemplateTarget.Set -> return TemplateRun(
            files = templateSetFilesFor(context, target.declaration, table, values),
            templateSet = target.declaration,
        )
        is TemplateTarget.Templates -> target.chosen
    }
    requireNoConflicts(chosen, values)

    val files = linkedMapOf<String, String>()
    val ownerOf = linkedMapOf<String, DeclaredTemplate>()
    for (template in chosen) {
        val (path, content) = templateFileFor(context, template, values)
        ownerOf[path]?.let { owner ->
            throw KatachiDuplicateTemplateOutputException(
                path = path,
                templates = listOf(owner.specifier, template.specifier).sorted(),
            )
        }
        files[path] = content
        ownerOf[path] = template
    }
    return TemplateRun(files, templateSet = null)
}

internal fun templateFileFor(
    context: ArchitectureProcessContext<*>,
    template: DeclaredTemplate,
    values: Map<String, String>,
): Pair<String, String> {
    val role = template.role
    val entries = template.entries
    requireValidCaptureValues(role, entries, values)

    val existingValues = existingCaptureValues(
        entries = entries,
        fileSystem = context.fileSystem,
        projectRoot = { findProjectRoot(context.fileSystem).path },
    )

    val entry = entries.first()

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
    val missingPathCaptures = variant.pathCaptures.map { it.name }.distinct().filterNot { it in values }
    if (missingPathCaptures.isNotEmpty()) {
        throw missingCaptureException(role, entries, missingPathCaptures, template.declaredAt, fileName = null, existingValues)
    }

    // Step 5: check the values, both on their own and once filled into the whole segment.
    requireValidFilledSegments(role, entry, variant, values)

    // A derived occurrence reads the converted value of its plain capture; the same name on two
    // levels reads the one value twice.
    val path = fillCapturedPath(entry.path, variant) { filledValueOf(it, values) }

    // Step 6: the filled path has to match the very pattern it was filled from -- structurally
    // certain once every capture has a valid value, so a mismatch here is katachi's own bug.
    if (!entry.glob.matches(path)) {
        throw KatachiTemplatePathMismatchException(template = template.specifier, pattern = entry.path, path = path)
    }

    // Step 7: never write outside the project.
    requireInsideProject(role, path, template.declaredAt)

    return path to content
}

private fun requireInsideProject(role: Role, path: String, declaredAt: DeclarationSite) {
    val climbs = path.startsWith('/') || path.split('/').any { it == ".." }
    if (!climbs) return
    throw KatachiTemplatePathOutsideProjectException(role = role.qualifiedName, path = path, declaredAt = declaredAt)
}

internal fun String.withFinalNewline(): String =
    if (isEmpty() || endsWith("\n")) this else this + "\n"
