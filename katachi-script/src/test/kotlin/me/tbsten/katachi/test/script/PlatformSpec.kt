package me.tbsten.katachi.test.script

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import me.tbsten.katachi.script.internal.ProcessLauncher
import me.tbsten.katachi.script.internal.ScriptContext
import me.tbsten.katachi.script.internal.WINDOWS_COMMAND_LINE_ARG
import me.tbsten.katachi.script.internal.WINDOWS_COMMAND_LINE_ENV
import me.tbsten.katachi.script.internal.argsOfWrapper
import me.tbsten.katachi.script.internal.executableNames
import me.tbsten.katachi.script.internal.findOnPath
import me.tbsten.katachi.script.internal.javaExecutableIn
import me.tbsten.katachi.script.internal.pathSeparatorOf
import me.tbsten.katachi.script.internal.runProcess
import me.tbsten.katachi.script.internal.runScript
import me.tbsten.katachi.script.internal.shellQuote
import me.tbsten.katachi.script.internal.splitWindowsCommandLine

/**
 * Windows is not a host the tests run on, so each place that depends on the host takes it as a value and is held against both
 * answers here (KATACHI-456). What cannot be seen from Linux (`bin/katachi.cmd`, `kotlin.bat`) runs on `windows-latest` in CI.
 */
class PlatformSpec : FreeSpec({
    "shellQuote" - {
        "POSIX は ' で囲み、Windows は \" で囲む（\" の中の \" は 2 つにする）" {
            shellQuote("a b", windows = false) shouldBe "'a b'"
            shellQuote("a b", windows = true) shouldBe "\"a b\""
            shellQuote("it's", windows = false) shouldBe "'it'\\''s'"
            shellQuote("say \"hi\"", windows = true) shouldBe "\"say \"\"hi\"\"\""
        }

        "囲まなくてよい文字だけなら、どちらもそのまま（Windows はパスの \\ も）" {
            shellQuote("com.example.app", windows = false) shouldBe "com.example.app"
            shellQuote("com.example.app", windows = true) shouldBe "com.example.app"
            shellQuote("C:\\Users\\me\\app", windows = true) shouldBe "C:\\Users\\me\\app"
            shellQuote("C:\\Users\\me\\app", windows = false) shouldBe "'C:\\Users\\me\\app'"
        }
    }

    "splitWindowsCommandLine は Windows のプログラムが自分のコマンドラインを読むとおりに分ける" - {
        mapOf(
            "scaffold --package com.example" to listOf("scaffold", "--package", "com.example"),
            "  scaffold \t --lang   en  " to listOf("scaffold", "--lang", "en"),
            "--package=com.example -Dkatachi.msg=a=b" to listOf("--package=com.example", "-Dkatachi.msg=a=b"),
            "--package \"@Serializable なルート\" @" to listOf("--package", "@Serializable なルート", "@"),
            "a \"\" b" to listOf("a", "", "b"),
            "--dir \"C:\\My Project\\\\\" next" to listOf("--dir", "C:\\My Project\\", "next"),
            "say \\\"hi\\\"" to listOf("say", "\"hi\""),
            "\"a \"\"b\"\" c\"" to listOf("a \"b\" c"),
            "C:\\Users\\me\\app" to listOf("C:\\Users\\me\\app"),
            "x\r\n" to listOf("x"),
            "" to emptyList(),
        ).forEach { (line, expected) ->
            "[$line]" { splitWindowsCommandLine(line) shouldBe expected }
        }
    }

    "argsOfWrapper" - {
        "bin/katachi.cmd が目印だけを渡したら、環境変数のコマンドラインを分けて使う" {
            argsOfWrapper(listOf(WINDOWS_COMMAND_LINE_ARG), mapOf(WINDOWS_COMMAND_LINE_ENV to "scaffold --package=a.b -Dx=1")) shouldBe
                listOf("scaffold", "--package=a.b", "-Dx=1")
        }

        "コマンドラインが空（環境変数が無い）なら引数なし" {
            argsOfWrapper(listOf(WINDOWS_COMMAND_LINE_ARG), emptyMap()) shouldBe emptyList()
        }

        "目印のほかに引数があるときや目印が無いときは、bin/katachi の目印を外すだけ" {
            val env = mapOf(WINDOWS_COMMAND_LINE_ENV to "ignored")
            argsOfWrapper(listOf("katachi-escaped:-Dx", "scaffold"), env) shouldBe listOf("-Dx", "scaffold")
            argsOfWrapper(listOf(WINDOWS_COMMAND_LINE_ARG, "x"), env) shouldBe listOf(WINDOWS_COMMAND_LINE_ARG, "x")
        }
    }

    "executableNames" - {
        "POSIX は名前のまま" {
            executableNames("java", windows = false, pathExt = null) shouldBe listOf("java")
        }

        "Windows は PATHEXT の拡張子を付けた名前から探す（PATHEXT が無ければ既定の 4 つ）" {
            executableNames("java", windows = true, pathExt = ".EXE;.CMD") shouldBe listOf("java.exe", "java.cmd", "java")
            executableNames("java", windows = true, pathExt = null) shouldBe listOf("java.com", "java.exe", "java.bat", "java.cmd", "java")
        }

        "Windows でも、拡張子がもう付いた名前はそのまま" {
            executableNames("kotlin.bat", windows = true, pathExt = ".EXE;.BAT") shouldBe listOf("kotlin.bat")
        }
    }

    "javaExecutableIn は bin/java か bin\\java.exe" {
        javaExecutableIn("/jdk", windows = false) shouldBe Path("/jdk", "bin", "java")
        javaExecutableIn("/jdk", windows = true) shouldBe Path("/jdk", "bin", "java.exe")
    }

    "PATH の区切りは POSIX が : で Windows が ;" {
        pathSeparatorOf(windows = false) shouldBe ':'
        pathSeparatorOf(windows = true) shouldBe ';'
    }

    "findOnPath" - {
        fun context(env: Map<String, String>, windows: Boolean) = ScriptContext(
            cwd = createTempDirectory("katachi-script-"),
            env = env,
            out = {},
            err = {},
            macOsJavaStub = null,
            windows = windows,
        )

        fun executable(dir: Path, name: String) {
            dir.createDirectories()
            dir.resolve(name).also { it.writeText("") }.toFile().setExecutable(true)
        }

        "Windows は ; で区切った PATH から、java.exe を java として見つける" {
            val first = createTempDirectory("katachi-path-")
            val second = createTempDirectory("katachi-path-")
            executable(second, "java.exe")

            val found = context(mapOf("PATH" to "$first;$second", "PATHEXT" to ".EXE"), windows = true).findOnPath("java")

            found shouldBe second.resolve("java.exe")
        }

        "Windows は環境変数の名前が Path でも PATH として読む" {
            val dir = createTempDirectory("katachi-path-")
            executable(dir, "git.exe")

            context(mapOf("Path" to dir.toString()), windows = true).findOnPath("git") shouldBe dir.resolve("git.exe")
        }

        "POSIX は : で区切り、拡張子を足さない" {
            val dir = createTempDirectory("katachi-path-")
            executable(dir, "java.exe")
            executable(dir, "java")

            context(mapOf("PATH" to "/no/such:$dir"), windows = false).findOnPath("java") shouldBe dir.resolve("java")
        }

        "どこにも無ければ null" {
            context(mapOf("PATH" to createTempDirectory("katachi-path-").toString()), windows = false).findOnPath("java") shouldBe null
        }
    }

    "runProcess は、標準入力を読むプログラムを待たせない（/dev/null に頼らず、入力を閉じる）" {
        val dir = createTempDirectory("katachi-script-")
        // `cat` ends only when its stdin does.
        runProcess(listOf("cat"), dir, timeoutSeconds = 20)?.status shouldBe 0
    }

    "Windows では kotlin.bat を起動する" {
        val project = createTempDirectory("katachi-script-")
        project.resolve("setup.katachi.kts").writeText("println(1)")
        val launched = mutableListOf<List<String>>()
        val launcher = object : ProcessLauncher {
            override fun launch(command: List<String>, extraEnv: Map<String, String>, cwd: Path): Int {
                launched += command
                return 0
            }
        }
        val failing = ProcessLauncher { _, _, _ -> throw IOException("not found") }

        fun run(windows: Boolean, with: ProcessLauncher): Pair<Int, String> {
            val err = StringBuilder()
            val context = ScriptContext(project, emptyMap(), {}, { err.appendLine(it) }, launch = with, macOsJavaStub = null, windows = windows)
            return runScript(listOf("setup.katachi.kts"), context) to err.toString()
        }

        run(windows = true, with = launcher).first shouldBe 0
        launched.single().first() shouldBe "kotlin.bat"
        // `kotlin.bat` splits at `;`, so the class path of several jars goes as one quoted argument.
        launched.single()[3].let { it.startsWith("\"") && it.endsWith("\"") && it.contains(";") } shouldBe true
        launched.clear()
        run(windows = false, with = launcher).first shouldBe 0
        launched.single().first() shouldBe "kotlin"
        launched.single()[3].contains("\"") shouldBe false

        val (status, err) = run(windows = true, with = failing)
        status shouldBe 1
        err.contains("winget install JetBrains.Kotlin.Compiler") shouldBe true
    }
})
