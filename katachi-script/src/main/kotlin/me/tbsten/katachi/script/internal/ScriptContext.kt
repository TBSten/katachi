package me.tbsten.katachi.script.internal

import java.io.OutputStream
import java.io.PrintStream
import java.nio.file.Path
import java.time.Clock
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

/** The name people type. */
internal const val CLI_COMMAND: String = "katachi"

/** What the install guide's steps start with. Every "next:" line and every step command in the bundled guide uses it. */
internal const val AGENT_COMMAND: String = "$CLI_COMMAND $AGENT_GROUP"

internal class ScriptContext(
    val cwd: Path,
    val env: Map<String, String>,
    val out: (String) -> Unit,
    val err: (String) -> Unit,
    val clock: Clock = Clock.systemDefaultZone(),
    val http: HttpFetcher = JdkHttpFetcher,
    val launch: ProcessLauncher = InheritingIoLauncher,
) {
    var lang: Lang = Lang.of(env["KATACHI_LANG"]) ?: Lang.JA

    /** `--lang` given on the command line (init / doctor); it wins over everything else. */
    var langExplicit: Boolean = false

    /** The write lock this process holds, so that a command that took it does not wait on itself. */
    var heldLock: WorkdirLock? = null

    fun pick(message: Msg): String = if (lang == Lang.EN) message.en else message.ja

    fun say(message: Msg) = out(pick(message))

    /** A line printed the same in both languages (`KEY=VALUE`, a bare path, a blank line). */
    fun say(text: String) = out(text)

    /** The kit's `note`: an indented detail line. */
    fun note(message: Msg) = out("   " + pick(message))

    fun note(text: String) = out("   $text")

    fun warn(message: Msg) = err("\n[!] ${pick(message)}")

    fun warn(text: String) = err("\n[!] $text")

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
    val text: Msg,
    val status: Int = 1,
    /** `true` prints the message bare, for callers that branch on the exit code rather than read it. */
    val plain: Boolean = false,
) : RuntimeException(text.en)

internal fun die(message: Msg): Nothing = throw KatachiScriptExitException(message)

/** Ends the command with [status] and nothing more on stderr (what was to be said is already printed). */
internal fun exitWith(status: Int): Nothing = throw KatachiScriptExitException(Msg("", ""), status = status, plain = true)

/** Stops with [message] printed bare on stderr, as the kit's embedded python did before `sys.exit(1)`. */
internal fun fail(message: Msg, status: Int = 1): Nothing = throw KatachiScriptExitException(message, status = status, plain = true)

/** `--opt` without a value: the kit's `need_value`. */
internal fun needValue(option: String, value: String?): String =
    value?.takeIf { it.isNotEmpty() } ?: die(Messages.Context.needsValue(option))

/**
 * A path for people, as the kit's `file_uri` prints it: absolute, `file://`, with only the four
 * characters a terminal would misread percent-encoded. Not for values a program reads back.
 */
internal fun ScriptContext.fileUri(path: String): String = fileUriOf(resolve(path))

internal fun ScriptContext.fileUri(path: Path): String = fileUri(path.toString())

internal fun fileUriOf(absolute: Path): String {
    val encoded = absolute.normalize().toString()
        .replace("%", "%25")
        .replace(" ", "%20")
        .replace("#", "%23")
        .replace("?", "%3F")
    return "file://$encoded"
}
