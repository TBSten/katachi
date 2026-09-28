package me.tbsten.katachi.template.internal

import me.tbsten.katachi.check.internal.moduleIndex
import me.tbsten.katachi.dsl.LayoutEntry
import me.tbsten.katachi.dsl.files.internal.findProjectRoot
import me.tbsten.katachi.dsl.internal.ModuleIndex
import me.tbsten.katachi.dsl.internal.ModuleMiss
import me.tbsten.katachi.dsl.internal.evaluateTemplate
import me.tbsten.katachi.dsl.internal.flattenLayout
import me.tbsten.katachi.processor.ArchitectureProcessContext
import me.tbsten.katachi.processor.internal.fileSystem

/**
 * What running [roleName]'s template with [values] would put in the project: path to contents.
 *
 * A pure function from declarations and values to a map, exactly as documentation generation is.
 * Everything that can be wrong about the output -- a name the layout does not accept, two places
 * that both accept it, a value that was never passed -- is decided here and can be pinned by a
 * spec comparing two maps. [writeTemplateFiles] adds the one thing a spec cannot compare.
 *
 * Paths are relative to the **project root**, `/` separated, which is what a `layout { }`
 * declares. The file system is read only when [values] fill in a module capture: which modules
 * exist is what such a value is checked against, and [modules] is asked for them then, once --
 * after every problem the declarations alone can show has been reported.
 */
internal fun templateFiles(
    context: ArchitectureProcessContext<*>,
    roleName: String,
    values: Map<String, String>,
    modules: () -> ModuleIndex = {
        moduleIndex(context.fileSystem, findProjectRoot(context.fileSystem).path, context.architecture.moduleResolver)
    },
): Map<String, String> {
    val role = templateRoleOf(context.roles, roleName)
    val entries = context.declaredEntries.filter { it.role === role }
    val template = templateOf(role)
    val captureNames = captureNamesOf(entries)
    requireNoCaptureConflicts(role, entries, template, values)
    requireValidCaptureValues(role, entries, values)

    val evaluation = evaluateTemplate(
        declaration = template,
        roleName = role.qualifiedName,
        values = values,
        captureNames = captureNames,
    )
    val layout = if (fillsModuleCapture(entries, values)) {
        val misses = mutableListOf<ModuleMiss>()
        PlacementLayout(role.flattenLayout(modules().boundTo(values, misses)), misses)
    } else {
        PlacementLayout(entries)
    }

    // Declaration order, so the log reads the way the template does. Two files of one template
    // cannot collide: their names differ -- `file(...)` refuses a repeat -- and a name decides
    // its own directory, so no two of them resolve to one path.
    val files = LinkedHashMap<String, String>(evaluation.files.size)
    for (file in evaluation.files) {
        val path = placeTemplateFile(
            role = role,
            layout = layout,
            fileName = file.fileName,
            declaredAt = file.declaredAt,
            values = values,
        )
        files[path] = file.content.withFinalNewline()
    }
    return files
}

/** Whether [values] give every name of some module capture of [entries], which only the modules that exist can place. */
private fun fillsModuleCapture(entries: List<LayoutEntry>, values: Map<String, String>): Boolean =
    entries.any { entry ->
        entry.captureVariants.any { variant -> variant.moduleCapture?.names?.all { it in values } == true }
    }

/**
 * [this] as a text file's contents: ending in a newline, the way every other file in the tree does.
 *
 * A template's block usually ends in `""".trimIndent()`, which stops at the last character the
 * author typed. What lands on disk is source the user's own tooling then reads, and a formatter or
 * an `.editorconfig` with `insert_final_newline` would report the first generated file as wrong --
 * a poor introduction from a generator whose whole promise is output the checks already accept.
 *
 * An empty file is left empty: a newline is the end of a line, and there is no line.
 */
private fun String.withFinalNewline(): String =
    if (isEmpty() || endsWith("\n")) this else this + "\n"
