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

/** The check list page as the katachi skill lays it out: the JSON block between tags that stand alone on their lines. */
internal fun checkListHtml(json: String, lang: String? = null): String = jsonBlockHtml("checklist", json, lang)

/** A page holding [json] in the block [id] (`checklist` / `report`), the tags alone on their lines. */
internal fun jsonBlockHtml(id: String, json: String, lang: String? = null): String = listOf(
    "<!doctype html>",
    if (lang == null) "<html>" else "<html lang=\"$lang\">",
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

/** A working directory the way the katachi skill lays it out, with [json] in `check-list.html` written in [lang]. */
internal fun workdirWith(json: String, lang: String? = "ja"): Path {
    val workdir = createTempDirectory("katachi-script-")
    workdir.resolve("tmp").createDirectories()
    workdir.resolve("check-list.html").writeText(checkListHtml(json, lang))
    return workdir
}

/** A Gradle project of one `settings.gradle.kts`, with the katachi skill's working directory in `.local/install-katachi` holding [json]. */
internal fun projectWithWorkdir(json: String, lang: String = "ja"): Path {
    val project = createTempDirectory("katachi-project-")
    project.resolve("settings.gradle.kts").writeText("rootProject.name = \"p\"\n")
    val workdir = project.resolve(".local/install-katachi")
    workdir.resolve("tmp").createDirectories()
    workdir.resolve("check-list.html").writeText(checkListHtml(json, lang))
    return project
}
