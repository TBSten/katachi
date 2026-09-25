package me.tbsten.katachi.template.internal

import me.tbsten.katachi.dsl.LayoutEntry
import me.tbsten.katachi.dsl.Role
import me.tbsten.katachi.dsl.internal.evaluateTemplate
import me.tbsten.katachi.processor.ArchitectureProcessContext

/**
 * What running [roleName]'s template with [values] would put in the project: path to contents.
 *
 * A pure function from declarations and values to a map, exactly as documentation generation is.
 * Everything that can be wrong about the output -- a name the layout does not accept, two places
 * that both accept it, a value that was never passed -- is decided here and can be pinned by a
 * spec comparing two maps. [writeTemplateFiles] adds the one thing a spec cannot compare.
 *
 * Paths are relative to the **project root**, `/` separated, which is what a `layout { }`
 * declares. Nothing here reads the file system, the search for the project root included.
 */
internal fun templateFiles(
    context: ArchitectureProcessContext<*>,
    roleName: String,
    values: Map<String, String>,
): Map<String, String> {
    val role = templateRoleOf(context.roles, roleName)
    return templateFilesOf(role, context.declaredEntries, values)
}

/** [templateFiles] once the role has been resolved. */
private fun templateFilesOf(
    role: Role,
    declaredEntries: List<LayoutEntry>,
    values: Map<String, String>,
): Map<String, String> {
    val evaluation = evaluateTemplate(
        declaration = templateOf(role),
        roleName = role.qualifiedName,
        values = values,
    )

    // Narrowed once rather than per file: a definition of any size holds far more entries than
    // one template holds files, and the answer is the same for every one of them.
    val entries = declaredEntries.filter { it.role === role }

    // Declaration order, so the log reads the way the template does. Two files of one template
    // cannot collide: their names differ -- `file(...)` refuses a repeat -- and a name decides
    // its own directory, so no two of them resolve to one path.
    val files = LinkedHashMap<String, String>(evaluation.files.size)
    for (file in evaluation.files) {
        val path = placeTemplateFile(
            role = role,
            entries = entries,
            fileName = file.fileName,
            declaredAt = file.declaredAt,
        )
        files[path] = file.content.withFinalNewline()
    }
    return files
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
