package me.tbsten.katachi.test.script

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import me.tbsten.katachi.script.internal.GradleRun
import me.tbsten.katachi.script.internal.GradleRunner

/*
 * The definition JSON as a spec writes it by hand: only the keys lint reads. DefinitionContractSpec writes the real one
 * with katachi's processor and reads it back, so a key renamed in :katachi fails there.
 */

/** A layout line: [kind] is Directory, File, AnyFile or Ignore. */
internal class JsonEntry(val path: String, val kind: String = "File", val synthetic: Boolean = false)

internal fun layoutFile(path: String): JsonEntry = JsonEntry(path, "File")

internal fun layoutAnyFile(path: String): JsonEntry = JsonEntry(path, "AnyFile")

internal fun layoutIgnore(path: String): JsonEntry = JsonEntry(path, "Ignore")

private fun siteJson(fileName: String?, line: Int): String =
    if (fileName == null) "null" else """{"fileName": "$fileName", "line": $line}"""

private fun Boolean?.json(): String = this?.toString() ?: "null"

internal fun roleJson(
    name: String,
    fileName: String?,
    line: Int = 1,
    groupPath: List<String> = emptyList(),
    documented: Boolean? = null,
    layout: List<JsonEntry> = emptyList(),
    constraints: Int = 0,
    declaredByGradle: Boolean? = false,
): String {
    val entries = layout.joinToString(",") { """{"path": "${it.path}", "kind": "${it.kind}", "synthetic": ${it.synthetic}}""" }
    val constraintList = (1..constraints).joinToString(",") { "{}" }
    val gradle = declaredByGradle?.let { """, "declaredByGradle": $it""" }.orEmpty()
    return """{"name": "$name", "groupPath": [${groupPath.joinToString(",") { "\"$it\"" }}], "documented": ${documented.json()},""" +
        """ "declaredAt": ${siteJson(fileName, line)}, "layout": [$entries], "constraints": [$constraintList]$gradle}"""
}

internal fun groupJson(
    name: String,
    fileName: String?,
    line: Int = 1,
    path: List<String> = listOf(name),
    documented: Boolean? = null,
    declaredByGradle: Boolean? = false,
): String {
    val gradle = declaredByGradle?.let { """, "declaredByGradle": $it""" }.orEmpty()
    return """{"name": "$name", "path": [${path.joinToString(",") { "\"$it\"" }}], "documented": ${documented.json()},""" +
        """ "declaredAt": ${siteJson(fileName, line)}$gradle}"""
}

internal fun definitionJson(groups: List<String> = emptyList(), roles: List<String> = emptyList(), schemaVersion: Int = 1): String =
    """{"schemaVersion": $schemaVersion, "groups": [${groups.joinToString(",")}], "roles": [${roles.joinToString(",")}]}"""

/** What a Gradle run was asked to do. */
internal class GradleCall(val root: Path, val arguments: List<String>)

/**
 * A Gradle that answers like the plugin's task: writes [json] under [moduleDir] and exits 0, or prints [failure] and exits [status].
 * [write] false is a run that exits 0 and leaves nothing new behind.
 */
internal class FakeGradle(
    private val moduleDir: String = "architecture-test",
    private val json: String = definitionJson(),
    private val status: Int = 0,
    private val failure: String = "",
    private val write: Boolean = true,
) : GradleRunner {
    val calls = mutableListOf<GradleCall>()

    override fun run(root: Path, arguments: List<String>): GradleRun {
        calls += GradleCall(root, arguments)
        if (status == 0 && write) {
            val output = root.resolve(moduleDir).resolve("build/katachi/internalDefinitionJson/definition.json")
            output.parent.createDirectories()
            output.writeText(json)
        }
        return GradleRun(status, failure)
    }
}

/** A project with a `gradlew`, as `lint` asks for, and [files] (name to source) under `architecture-test/src/test/kotlin`. */
internal fun gradleProject(vararg files: Pair<String, String>): Path {
    val project = createTempDirectory("katachi-project-")
    Files.createFile(project.resolve("gradlew"))
    val dir = project.resolve("architecture-test/src/test/kotlin").createDirectories()
    for ((name, source) in files) dir.resolve(name).also { it.parent.createDirectories() }.writeText(source + "\n")
    return project
}
