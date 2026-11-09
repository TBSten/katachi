package me.tbsten.katachi.test.script

import io.kotest.core.spec.style.FreeSpec
import java.io.File
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.string.shouldStartWith
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import me.tbsten.katachi.script.internal.COMMAND_SPECS
import me.tbsten.katachi.script.internal.Lang
import me.tbsten.katachi.script.internal.ScriptContext
import me.tbsten.katachi.script.internal.agentUsage
import me.tbsten.katachi.script.internal.usage

/** Every subcommand runs in Kotlin; nothing is handed to katachi-install.sh any more (KATACHI-228 / 229). */
class RunScriptSpec : FreeSpec({
    "--version は katachi-script の版を出す" {
        val run = runCapturing(createTempDirectory("katachi-script-"), listOf("--version"))
        run.status shouldBe 0
        run.out shouldStartWith "katachi "
    }

    "--help は katachi … の形の使い方を出し、sh のキットを名指ししない" {
        val run = runCapturing(createTempDirectory("katachi-script-"), listOf("--help"))
        run.status shouldBe 0
        run.out shouldContain "katachi scaffold --package"
        run.out shouldNotContain "katachi-install.sh"
        run.out shouldNotContain "sh <CLI>"
    }

    "--help は人が打つコマンドだけを並べ、agent のコマンドは agent の 1 行にまとめる" {
        val run = runCapturing(createTempDirectory("katachi-script-"), listOf("--help"))
        run.out shouldContain "katachi doctor"
        run.out shouldContain "katachi lint"
        run.out shouldContain "katachi agent <サブコマンド>"
        run.out shouldNotContain "katachi agent init"
        run.out shouldNotContain "katachi init"
    }

    "agent の --help は手順書のステップが使うコマンドを並べる" {
        for (args in listOf(listOf("agent"), listOf("agent", "--help"))) {
            val run = runCapturing(createTempDirectory("katachi-script-"), args)
            run.status shouldBe 0
            run.out shouldContain "katachi agent init"
            run.out shouldContain "katachi agent check <項目 id>..."
            run.out shouldNotContain "katachi scaffold --package"
        }
    }

    "agent のコマンドをトップで打つと agent の下にあると言って止まる" {
        val run = runCapturing(createTempDirectory("katachi-script-"), listOf("init", "--lang", "ja"))
        run.status shouldBe 1
        run.err shouldContain "init は 'katachi agent init' です"
    }

    "人が打つコマンドは agent の下には無い" {
        val run = runCapturing(createTempDirectory("katachi-script-"), listOf("agent", "scaffold"))
        run.status shouldBe 1
        run.err shouldContain "katachi agent のサブコマンドではありません: scaffold"
    }

    "サブコマンドの --help は値として読まずに使い方を出す" {
        val run = runCapturing(createTempDirectory("katachi-script-"), listOf("agent", "data", "get", "--help"))
        run.status shouldBe 0
        run.out shouldContain "katachi agent data get"
    }

    "知らないサブコマンドは止まる" {
        val run = runCapturing(createTempDirectory("katachi-script-"), listOf("nothing"))
        run.status shouldBe 1
        run.err shouldContain "[x] 知らないサブコマンドです: nothing"
    }

    "使い方には spec にあるオプションがすべて載っている（ja / en）" {
        for (lang in Lang.entries) {
            val context = ScriptContext(createTempDirectory("katachi-script-"), emptyMap(), {}, {}).also { it.lang = lang }
            val text = context.usage() + "\n" + context.agentUsage()
            // add lists the fields of each kind in `add <kind> --help`, which reads the same ADD_KINDS.
            val missing = COMMAND_SPECS.filter { it.name != "add" }
                .flatMap { spec -> (spec.options + spec.subcommands.flatMap { it.options }).map { spec.path.joinToString(" ") to it } }
                .filter { (command, option) ->
                    if (option.keyValue) {
                        !text.contains(option.name)
                    } else {
                        text.lines().none { it.contains("katachi $command") && it.contains(option.name) } && !text.contains("      ${option.name}")
                    }
                }
                .map { (command, option) -> command to option.name }
            missing.shouldBeEmpty()
        }
    }

    "guide は katachi skill の setup の表の順に、どのファイルのセクションも出す（KATACHI-318）" {
        val setup = File("../skills/katachi/references/setup")
        val files = setupFilesInTableOrder()
        val listed = files.map { it.relativeTo(setup).path }.toSet()
        val onDisk = setup.walkTopDown().filter { it.isFile && it.parentFile != setup }.map { it.relativeTo(setup).path }.toSet()
        onDisk shouldBe listed
        val toc = runCapturing(createTempDirectory("katachi-script-"), listOf("agent", "guide")).out
        val ids = toc.lines().filter { '\t' in it }.map { it.substringBefore('\t') }
        ids shouldBe files.map { it.readLines().first().split(" ")[1].removeSuffix(".") }
    }

    "guide は init の前でも同梱の手順書から目次を出す" {
        val run = runCapturing(createTempDirectory("katachi-script-"), listOf("agent", "guide", "--lang", "en"))
        run.status shouldBe 0
        run.out shouldContain "0-2\tRun init"
    }

    "guide <セクション> の次の案内は katachi agent guide の形" {
        val run = runCapturing(createTempDirectory("katachi-script-"), listOf("agent", "guide", "1-1"))
        run.status shouldBe 0
        run.out.trimEnd().lines().last() shouldBe "次: katachi agent guide 1-2"
        run.out shouldNotContain "sh \$CLI"
    }

    "guide --lang を付けたときは、次の案内にも同じ --lang を付ける（init の前は言語の記録が無い）" {
        val run = runCapturing(createTempDirectory("katachi-script-"), listOf("agent", "guide", "0", "--lang", "en"))
        run.status shouldBe 0
        run.out.trimEnd().lines().last() shouldBe "Next: katachi agent guide 0-1 --lang en"
        val next = runCapturing(createTempDirectory("katachi-script-"), listOf("agent", "guide", "0-1", "--lang", "en"))
        next.out.trimEnd().lines().last() shouldBe "Next: katachi agent guide 0-2 --lang en"
    }

    "init --offline はネットワークなしで、同梱のテンプレートと手順書を置く" {
        val project = createTempDirectory("katachi-project-")
        project.resolve("settings.gradle.kts").writeText("rootProject.name = \"p\"\n")
        project.resolve("build.gradle.kts").writeText("plugins {\n    kotlin(\"jvm\") version \"2.4.10\"\n}\n")
        val run = runCapturing(project, listOf("agent", "init", "--offline", "--katachi", "0.4.0", "--lang", "ja", "--workdir", ".local/install-katachi"))
        run.status shouldBe 0
        val workdir = project.resolve(".local/install-katachi")
        workdir.resolve("check-list.html").readText() shouldContain "\"version\": \"0.4.0\""
        workdir.resolve("check-list.html").readText() shouldNotContain "sh katachi-install.sh "
        workdir.resolve("project-code-base-report.html").exists() shouldBe true
        // The bundled guide is the katachi skill's setup steps, joined in the order of the table in its README.
        workdir.resolve("tmp/cache/index.md").readText() shouldBe skillSetupGuide()
        workdir.resolve("tmp/cache/lang").readText() shouldBe "ja"
        workdir.resolve("tmp/katachi-install.sh").exists() shouldBe false
        // The guide is written for the katachi command itself, so nothing is left to rewrite (KATACHI-287).
        workdir.resolve("tmp/cache/index.md").readText() shouldNotContain "sh \$CLI"
        workdir.resolve("tmp/cache/index.md").readText() shouldNotContain "/tmp/katachi-install.sh"
        run.out shouldNotContain "KATACHI_CLI="
        run.out shouldContain "KATACHI_KOTLIN=2.4.10"
        run.out.trimEnd().lines().last() shouldStartWith "次: katachi agent check 0-1 0-2"
    }

    "init のあとは同じディレクトリから作業用ディレクトリを見つけ、記録した言語で答える" {
        val project = createTempDirectory("katachi-project-")
        project.resolve("settings.gradle.kts").writeText("rootProject.name = \"p\"\n")
        runCapturing(project, listOf("agent", "init", "--offline", "--katachi", "0.4.0", "--lang", "en", "--workdir", ".local/install-katachi")).status shouldBe 0
        val check = runCapturing(project, listOf("agent", "check", "0-1", "0-2"))
        check.status shouldBe 0
        check.out shouldContain "Marked as done: 0-1, 0-2"
        check.out shouldContain "Next: katachi agent guide 0-3"
        val verify = runCapturing(project, listOf("agent", "verify"))
        verify.status shouldBe 1
        verify.out shouldContain "check-list: not done 1-1"
    }

    "scaffold --dry-run は何も書かずに作る内容を出す" {
        val project = createTempDirectory("katachi-project-")
        project.resolve("settings.gradle.kts").writeText("rootProject.name = \"p\"\ninclude(\":app\")\n")
        project.resolve("build.gradle.kts").writeText("plugins {\n    kotlin(\"jvm\") version \"2.4.10\" apply false\n}\n")
        project.resolve("app").createDirectories()
        val run = runCapturing(project, listOf("scaffold", "--package", "com.example", "--katachi", "0.4.0", "--dry-run"), env = mapOf("JAVA_HOME" to System.getProperty("java.home")))
        run.status shouldBe 0
        run.out shouldContain "include 追加: yes"
        run.out shouldContain "--dry-run のため何も書きませんでした。"
        project.resolve("architecture-test").exists() shouldBe false
    }

    "scaffold は --package が無ければ例つきで止まる" {
        val run = runCapturing(createTempDirectory("katachi-project-"), listOf("scaffold"))
        run.status shouldBe 1
        run.err shouldContain "--package が必要です（例: --package com.example.app）"
    }
})

/** The setup steps of the katachi skill, in the order of the table in its README (the order `guide` reads them in). */
private fun setupFilesInTableOrder(): List<File> {
    val setup = File("../skills/katachi/references/setup")
    return Regex("""\| \[(step[0-9]+/[^\]]+\.md)]""").findAll(setup.resolve("README.md").readText())
        .map { setup.resolve(it.groupValues[1]) }
        .toList()
}

private fun skillSetupGuide(): String {
    val setup = File("../skills/katachi/references/setup")
    return (listOf(setup.resolve("README.md")) + setupFilesInTableOrder()).joinToString("\n") { it.readText() }
}
