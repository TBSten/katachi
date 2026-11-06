package me.tbsten.katachi.test.script

import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import me.tbsten.katachi.script.internal.ScriptContext
import me.tbsten.katachi.script.internal.runScript

internal class RecordedRun(val status: Int, val out: String, val err: String)

/** Runs the command as `main` would, with stdout and stderr captured. */
internal fun runCapturing(
    cwd: Path,
    args: List<String>,
    env: Map<String, String> = emptyMap(),
): RecordedRun {
    val out = StringBuilder()
    val err = StringBuilder()
    val context = ScriptContext(cwd, env, { out.appendLine(it) }, { err.appendLine(it) })
    val status = runScript(args, context)
    return RecordedRun(status, out.toString(), err.toString())
}

/** The check list page as `init` leaves it: the JSON block between tags that stand alone on their lines. */
internal fun checkListHtml(json: String): String = jsonBlockHtml("checklist", json)

/** A page holding [json] in the block [id] (`checklist` / `report`), the tags alone on their lines. */
internal fun jsonBlockHtml(id: String, json: String): String = listOf(
    "<!doctype html>",
    "<html>",
    "<head><title>$id</title></head>",
    "<body>",
    // Built line by line: a multi-line [json] inside a trimIndent template would shift the tags off the line start.
    "<script type=\"application/json\" id=\"$id\">",
    json,
    "</script>",
    "<script>render()</script>",
    "</body>",
    "</html>",
).joinToString("\n", postfix = "\n")

/** A working directory the way `init` lays it out, with [json] in `check-list.html`. */
internal fun workdirWith(json: String, lang: String? = "ja"): Path {
    val workdir = createTempDirectory("katachi-script-")
    workdir.resolve("tmp/cache").createDirectories()
    workdir.resolve("check-list.html").writeText(checkListHtml(json))
    if (lang != null) workdir.resolve("tmp/cache/lang").writeText(lang + "\n")
    return workdir
}

/** [workdir]'s `project-code-base-report.html`, holding [json]. */
internal fun Path.writeReport(json: String): Path = resolve("project-code-base-report.html").also { it.writeText(jsonBlockHtml("report", json)) }

/** The JSON block [id] of [html], parsed. */
internal fun jsonBlockOf(html: Path, id: String): JsonObject {
    val text = html.readText()
    val start = text.indexOf("<script type=\"application/json\" id=\"$id\">\n")
    check(start >= 0) { "no block $id in $html" }
    val from = text.indexOf('\n', start) + 1
    val to = text.indexOf("\n</script>", from)
    return Json.parseToJsonElement(text.substring(from, to)).jsonObject
}

/** A Gradle project of one `settings.gradle.kts`, `init --offline` already run in `.local/install-katachi`. */
internal fun initializedProject(lang: String = "ja"): Path {
    val project = createTempDirectory("katachi-project-")
    project.resolve("settings.gradle.kts").writeText("rootProject.name = \"p\"\n")
    val run = runCapturing(project, listOf("init", "--offline", "--katachi", "0.4.0", "--lang", lang, "--workdir", ".local/install-katachi"))
    check(run.status == 0) { "init failed: ${run.err}" }
    return project
}

/** The working directory [initializedProject] made. */
internal val Path.installWorkdir: Path get() = resolve(".local/install-katachi")
