package me.tbsten.katachi.test.script

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import me.tbsten.katachi.script.internal.ShellRunner

/** Which subcommands the Kotlin side answers, and where the rest are sent. */
class RunScriptSpec : FreeSpec({
    class Recording : ShellRunner {
        var command: List<String>? = null
        var workingDirectory: Path? = null

        override fun run(command: List<String>, workingDirectory: Path): Int {
            this.command = command
            this.workingDirectory = workingDirectory
            return 7
        }
    }

    "--version は katachi-script の版を出す" {
        val run = runCapturing(createTempDirectory("katachi-script-"), listOf("--version"))
        run.status shouldBe 0
        run.out shouldStartWith "katachi "
    }

    "Kotlin に無いサブコマンドは KATACHI_HOME の katachi-install.sh に渡し、終了コードをそのまま返す" {
        val home = createTempDirectory("katachi-home-")
        home.resolve("katachi-install.sh").writeText("#!/bin/sh\n")
        val cwd = createTempDirectory("katachi-project-")
        val runner = Recording()
        val run = runCapturing(cwd, listOf("init", "--lang", "ja"), env = mapOf("KATACHI_HOME" to home.toString()), runner = runner)
        run.status shouldBe 7
        runner.command shouldBe listOf("sh", home.resolve("katachi-install.sh").toString(), "init", "--lang", "ja")
        runner.workingDirectory shouldBe cwd
    }

    "init が作業用ディレクトリに写した katachi-install.sh があればそちらに渡す" {
        val home = createTempDirectory("katachi-home-")
        home.resolve("katachi-install.sh").writeText("#!/bin/sh\n")
        val workdir = workdirWith("{}")
        workdir.resolve("tmp/katachi-install.sh").writeText("#!/bin/sh\n")
        val runner = Recording()
        runCapturing(workdir, listOf("summary"), env = mapOf("KATACHI_HOME" to home.toString()), runner = runner)
        runner.command?.get(1) shouldBe workdir.resolve("tmp/katachi-install.sh").toString()
    }

    "data --help も katachi-install.sh の usage に任せる" {
        val home = createTempDirectory("katachi-home-")
        home.resolve("katachi-install.sh").writeText("#!/bin/sh\n")
        val runner = Recording()
        runCapturing(createTempDirectory("katachi-project-"), listOf("data", "--help"), env = mapOf("KATACHI_HOME" to home.toString()), runner = runner)
        runner.command?.drop(2) shouldBe listOf("data", "--help")
    }

    "katachi-install.sh がどこにも無ければ止まる" {
        val cwd = createTempDirectory("katachi-project-").also { it.resolve("src").createDirectories() }
        val run = runCapturing(cwd, listOf("doctor"), runner = Recording())
        run.status shouldBe 1
        run.err shouldContain "[x] katachi-install.sh が見つかりません"
    }
})
