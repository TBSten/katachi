package me.tbsten.katachi.test.script

import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
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
internal fun checkListHtml(json: String): String =
    """
    <!doctype html>
    <html>
    <head><title>check list</title></head>
    <body>
    <script type="application/json" id="checklist">
    $json
    </script>
    <script>render()</script>
    </body>
    </html>
    """.trimIndent() + "\n"

/** A working directory the way `init` lays it out, with [json] in `check-list.html`. */
internal fun workdirWith(json: String, lang: String? = "ja"): Path {
    val workdir = createTempDirectory("katachi-script-")
    workdir.resolve("tmp/cache").createDirectories()
    workdir.resolve("check-list.html").writeText(checkListHtml(json))
    if (lang != null) workdir.resolve("tmp/cache/lang").writeText(lang + "\n")
    return workdir
}
