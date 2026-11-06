package me.tbsten.katachi.intellij.data.script

import java.nio.file.InvalidPathException
import java.nio.file.Path
import kotlin.io.path.Path

/** The file name ending of katachi's scripts (KATACHI-101). */
internal const val KATACHI_SCRIPT_SUFFIX = ".katachi.kts"

internal fun isKatachiScript(fileName: String): Boolean = fileName.endsWith(KATACHI_SCRIPT_SUFFIX)

/** What `katachi internal script-classpath` told: the jars of the `*.katachi.kts` definition, or why there are none. */
internal sealed interface ScriptClasspath {
    data class Found(val jars: List<Path>) : ScriptClasspath

    /** No `katachi` on PATH nor in the usual install directories. */
    data object CliNotFound : ScriptClasspath

    /** A katachi from before `*.katachi.kts`: it does not know the subcommand. */
    data class Unsupported(val detail: String) : ScriptClasspath

    /** It ran, but what it printed is not a list of jars (or it did not finish). */
    data class Malformed(val detail: String) : ScriptClasspath
}

/** How the `katachi` process ended. A null [exitCode] is one that did not finish in time. */
internal data class ProcessOutcome(val exitCode: Int?, val stdout: String, val stderr: String)

internal object ScriptClasspathInterpreter {
    fun interpret(outcome: ProcessOutcome, exists: (Path) -> Boolean): ScriptClasspath {
        val exitCode = outcome.exitCode ?: return ScriptClasspath.Malformed("katachi internal script-classpath did not finish")
        if (exitCode != 0) {
            return ScriptClasspath.Unsupported("exit $exitCode: ${outcome.stderr.trim().lines().firstOrNull().orEmpty()}")
        }
        val lines = outcome.stdout.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return ScriptClasspath.Malformed("katachi internal script-classpath printed nothing")
        val jars = lines.map { line ->
            val path = pathOrNull(line)
            if (path == null || !path.isAbsolute || !exists(path)) {
                return ScriptClasspath.Malformed("not a file: $line")
            }
            path
        }
        return ScriptClasspath.Found(jars)
    }

    private fun pathOrNull(text: String): Path? = try {
        Path(text)
    } catch (_: InvalidPathException) {
        null
    }
}

/** Where the katachi CLI's jars announce the `*.katachi.kts` definition to `kotlin` and the IDE. */
internal const val KATACHI_SCRIPT_DEFINITION_MARKER = "META-INF/kotlin/script/templates/me.tbsten.katachi.script.KatachiScript.classname"

/**
 * Why [jars] cannot hold the `*.katachi.kts` definition, or null when they can: each one opens as a jar and one of them
 * has the definition's marker. Checked before the Kotlin plugin reads them, whose own reading of a broken jar is not
 * ours to guard.
 */
internal fun definitionJarsProblem(jars: List<Path>): String? {
    var marked = false
    for (jar in jars) {
        try {
            java.util.zip.ZipFile(jar.toFile()).use { zip -> if (zip.getEntry(KATACHI_SCRIPT_DEFINITION_MARKER) != null) marked = true }
        } catch (failure: java.io.IOException) {
            return "not a jar: $jar (${failure.message})"
        }
    }
    return if (marked) null else "no *.katachi.kts definition in $jars"
}
