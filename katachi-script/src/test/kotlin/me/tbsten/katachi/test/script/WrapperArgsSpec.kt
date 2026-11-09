package me.tbsten.katachi.test.script

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import me.tbsten.katachi.script.internal.unescapeWrapperArgs

/** `bin/katachi` hides `-D…` / `-J…` / `@…` from `kotlin`, and main() gets them back as typed (KATACHI-297, KATACHI-325). */
class WrapperArgsSpec : FreeSpec({
    "bin/katachi を通しても、引数は打ったとおりに main() に届く" - {
        listOf(
            "test の -- の後ろの -D" to listOf("agent", "test", "--", "-Dkatachi.baseline.update=true"),
            "-J と、値に空白や = を含む -D" to listOf("agent", "test", "--", "-Jx", "-Dkatachi.msg=a b=c", "--no-daemon"),
            "@ で始まる値（kotlin が argfile として読む）" to listOf("agent", "add", "--allowed", "@Serializable なルート", "@"),
            "目印そのものから始まる引数" to listOf("katachi-escaped:-Dx", "katachi-escaped:"),
            "-D でも -J でもない引数" to listOf("data", "get", "check-list", "meta.moduleDir", "-d", "--", "-"),
            "引数なし" to emptyList(),
        ).forEach { (name, typed) ->
            name {
                val reached = argsKotlinReceives(typed)
                reached.none { it.startsWith("-D") || it.startsWith("-J") || it.startsWith("@") } shouldBe true
                unescapeWrapperArgs(reached) shouldBe typed
            }
        }
    }
})

/** Runs the real `cli/bin/katachi` with a stand-in `kotlin` on PATH and returns what follows the script path. */
private fun argsKotlinReceives(args: List<String>): List<String> {
    val bin = createTempDirectory("katachi-wrapper")
    val out = bin.resolve("args")
    bin.resolve("kotlin").apply {
        writeText(
            """
            #!/bin/sh
            shift
            : > '$out'
            for arg do printf '%s\0' "${'$'}arg" >> '$out'; done
            """.trimIndent(),
        )
        toFile().setExecutable(true)
    }
    val process = ProcessBuilder(listOf("sh", File("cli/bin/katachi").absolutePath) + args)
        .apply { environment()["PATH"] = "$bin${File.pathSeparator}${System.getenv("PATH")}" }
        .redirectErrorStream(true)
        .start()
    val log = process.inputStream.bufferedReader().readText()
    check(process.waitFor() == 0) { "bin/katachi failed: $log" }
    return out.toFile().readText().split('\u0000').dropLast(1)
}
