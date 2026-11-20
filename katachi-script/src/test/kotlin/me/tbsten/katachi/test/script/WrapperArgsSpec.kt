package me.tbsten.katachi.test.script

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import me.tbsten.katachi.script.internal.SHELL_JAVA_HOME_ENV
import me.tbsten.katachi.script.internal.unescapeWrapperArgs

/**
 * `bin/katachi` hides `-D…` / `-J…` / `@…` from `kotlin`, and main() gets them back as typed (KATACHI-297, KATACHI-325).
 * It also passes on the shell's JAVA_HOME, which Homebrew's `kotlin` fills in when it is unset or empty (KATACHI-434).
 */
class WrapperArgsSpec : FreeSpec({
    "bin/katachi を通しても、引数は打ったとおりに main() に届く" - {
        listOf(
            "-- の後ろの -D" to listOf("scaffold", "--", "-Dkatachi.baseline.update=true"),
            "-J と、値に空白や = を含む -D" to listOf("scaffold", "--", "-Jx", "-Dkatachi.msg=a b=c", "--no-daemon"),
            "@ で始まる値（kotlin が argfile として読む）" to listOf("scaffold", "--package", "@Serializable なルート", "@"),
            "目印そのものから始まる引数" to listOf("katachi-escaped:-Dx", "katachi-escaped:"),
            "-D でも -J でもない引数" to listOf("scaffold", "--package", "com.example", "-d", "--", "-"),
            "引数なし" to emptyList(),
        ).forEach { (name, typed) ->
            name {
                val reached = argsKotlinReceives(typed)
                reached.none { it.startsWith("-D") || it.startsWith("-J") || it.startsWith("@") } shouldBe true
                unescapeWrapperArgs(reached) shouldBe typed
            }
        }
    }

    "bin/katachi は、kotlin が JAVA_HOME を埋める前のシェルの JAVA_HOME を KATACHI_SHELL_JAVA_HOME で渡す（KATACHI-434）" - {
        "JAVA_HOME が無ければ空で渡り、kotlin が入れた JAVA_HOME とは別に残る" {
            val seen = envKotlinReceives(emptyMap())

            seen[SHELL_JAVA_HOME_ENV] shouldBe ""
            seen["JAVA_HOME"] shouldBe HOMEBREW_OPENJDK
        }

        "JAVA_HOME が空なら空で渡る" {
            val seen = envKotlinReceives(mapOf("JAVA_HOME" to ""))

            seen[SHELL_JAVA_HOME_ENV] shouldBe ""
            seen["JAVA_HOME"] shouldBe HOMEBREW_OPENJDK
        }

        "JAVA_HOME に値があれば、その値で渡る" {
            val seen = envKotlinReceives(mapOf("JAVA_HOME" to "/opt/jdk-21"))

            seen[SHELL_JAVA_HOME_ENV] shouldBe "/opt/jdk-21"
            seen["JAVA_HOME"] shouldBe "/opt/jdk-21"
        }

        "呼び出し元から引き継いだ値は使わずに書き直す" {
            val seen = envKotlinReceives(mapOf(SHELL_JAVA_HOME_ENV to "/opt/jdk-8"))

            seen[SHELL_JAVA_HOME_ENV] shouldBe ""
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

/** Where Homebrew's `kotlin` points an unset or empty JAVA_HOME. */
private const val HOMEBREW_OPENJDK = "/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home"

/**
 * Runs the real `cli/bin/katachi` with only PATH and [env] set, under a stand-in for Homebrew's `kotlin` that fills an
 * unset or empty JAVA_HOME as the real one does, and returns the environment the JVM would have been started with.
 */
private fun envKotlinReceives(env: Map<String, String>): Map<String, String> {
    val bin = createTempDirectory("katachi-wrapper")
    val out = bin.resolve("env")
    bin.resolve("kotlin").apply {
        writeText(
            """
            #!/bin/sh
            JAVA_HOME=${'$'}{JAVA_HOME:-$HOMEBREW_OPENJDK}
            export JAVA_HOME
            env > '$out'
            """.trimIndent(),
        )
        toFile().setExecutable(true)
    }
    val process = ProcessBuilder(listOf("sh", File("cli/bin/katachi").absolutePath, "doctor"))
        .apply {
            environment().clear()
            environment()["PATH"] = "$bin${File.pathSeparator}${System.getenv("PATH")}"
            environment().putAll(env)
        }
        .redirectErrorStream(true)
        .start()
    val log = process.inputStream.bufferedReader().readText()
    check(process.waitFor() == 0) { "bin/katachi failed: $log" }
    return out.toFile().readLines().filter { '=' in it }.associate { it.substringBefore('=') to it.substringAfter('=') }
}
