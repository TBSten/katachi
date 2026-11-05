package me.tbsten.katachi.script.internal

import java.io.OutputStream
import java.io.PrintStream
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolute

internal enum class Lang(val code: String) {
    JA("ja"),
    EN("en"),
    ;

    companion object {
        fun of(code: String?): Lang? = entries.firstOrNull { it.code == code }
    }
}

internal class ScriptContext(
    val cwd: Path,
    val env: Map<String, String>,
    val out: (String) -> Unit,
    val err: (String) -> Unit,
) {
    var lang: Lang = Lang.of(env["KATACHI_LANG"]) ?: Lang.JA

    /** Where `bin/katachi` unpacked the kit; null when run from a bare `kotlin katachi.main.kts`. */
    val home: Path? = env["KATACHI_HOME"]?.takeIf { it.isNotBlank() }?.let { cwd.resolve(it).normalize() }

    fun pick(ja: String, en: String): String = if (lang == Lang.EN) en else ja

    fun say(ja: String, en: String) = out(pick(ja, en))

    fun warn(ja: String, en: String) = err("\n[!] ${pick(ja, en)}")

    fun resolve(path: String): Path = cwd.resolve(path).normalize()

    companion object {
        fun fromSystem(): ScriptContext {
            // The guide's messages are Japanese; under a POSIX locale the JVM's default stdout
            // encoding would print them as '?', while the sh kit always wrote UTF-8 bytes.
            val out = utf8(System.out)
            val err = utf8(System.err)
            return ScriptContext(
                cwd = Path("").absolute(),
                env = System.getenv(),
                out = out::println,
                err = err::println,
            )
        }

        private fun utf8(stream: OutputStream): PrintStream = PrintStream(stream, true, Charsets.UTF_8)
    }
}

/** Stops the command the way the kit's `die` does: `[x] message` on stderr, exit 1. */
internal class KatachiScriptExitException(
    val ja: String,
    val en: String,
    val status: Int = 1,
    /** `true` prints the message bare, for callers that branch on the exit code rather than read it. */
    val plain: Boolean = false,
) : RuntimeException(en)

internal fun die(ja: String, en: String): Nothing = throw KatachiScriptExitException(ja, en)

/** `--opt` without a value: the kit's `need_value`. */
internal fun needValue(option: String, value: String?): String =
    value?.takeIf { it.isNotEmpty() } ?: die("$option に値がありません。", "$option needs a value.")

/**
 * A path for people, as the kit's `file_uri` prints it: absolute, `file://`, with only the four
 * characters a terminal would misread percent-encoded. Not for values a program reads back.
 */
internal fun ScriptContext.fileUri(path: String): String {
    val absolute = resolve(path).toString()
    val encoded = absolute
        .replace("%", "%25")
        .replace(" ", "%20")
        .replace("#", "%23")
        .replace("?", "%3F")
    return "file://$encoded"
}

internal fun ScriptContext.fileUri(path: Path): String = fileUri(path.toString())
