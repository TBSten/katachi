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

    "agent の --help は katachi skill の手順が使うコマンドを並べる" {
        for (args in listOf(listOf("agent"), listOf("agent", "--help"))) {
            val run = runCapturing(createTempDirectory("katachi-script-"), args)
            run.status shouldBe 0
            run.out shouldContain "katachi agent test"
            run.out shouldContain "katachi agent compare-violations"
            run.out shouldNotContain "katachi scaffold --package"
        }
    }

    "agent のコマンドをトップで打つと agent の下にあると言って止まる" {
        val run = runCapturing(createTempDirectory("katachi-script-"), listOf("test"))
        run.status shouldBe 1
        run.err shouldContain "test は 'katachi agent test' です"
    }

    "katachi skill に置き換えたコマンドは agent の下にもない（KATACHI-329）" {
        for (command in listOf("init", "guide", "docs", "data", "add", "check", "uncheck", "warn", "verify", "summary", "preferences", "answers")) {
            val run = runCapturing(createTempDirectory("katachi-script-"), listOf("agent", command))
            run.status shouldBe 1
            run.err shouldContain "katachi agent $command は無くなりました"
            run.err shouldContain "gh skill install TBSten/katachi katachi"
            val top = runCapturing(createTempDirectory("katachi-script-"), listOf(command))
            top.status shouldBe 1
            top.err shouldContain "$command は無くなりました"
        }
    }

    "作業用ディレクトリのチェックリストの言語で答える" {
        val workdir = workdirWith("""{"meta": {}, "steps": []}""", lang = "en")
        val run = runCapturing(workdir, listOf("agent", "compare-violations"))
        run.status shouldBe 1
        run.err shouldContain "The definition directory does not exist"
    }

    "人が打つコマンドは agent の下には無い" {
        val run = runCapturing(createTempDirectory("katachi-script-"), listOf("agent", "scaffold"))
        run.status shouldBe 1
        run.err shouldContain "katachi agent のサブコマンドではありません: scaffold"
    }

    "サブコマンドの --help は値として読まずに使い方を出す" {
        val run = runCapturing(createTempDirectory("katachi-script-"), listOf("agent", "test", "--help"))
        run.status shouldBe 0
        run.out shouldContain "katachi agent test [--save"
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

    "katachi skill の setup の表は、どのファイルも載せ、各ファイルの見出しの ID と表の ID が合う（KATACHI-318）" {
        val setup = File("../skills/katachi/references/setup")
        val rows = setupTableRows()
        val listed = rows.map { it.second }.toSet()
        val onDisk = setup.walkTopDown().filter { it.isFile && it.parentFile != setup }.map { it.relativeTo(setup).path }.toSet()
        onDisk shouldBe listed
        for ((id, path) in rows) {
            setup.resolve(path).readLines().first().split(" ")[1].removeSuffix(".") shouldBe id
        }
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

/** The rows of the table in the katachi skill's setup README: (section ID, file relative to references/setup). */
private fun setupTableRows(): List<Pair<String, String>> {
    val readme = File("../skills/katachi/references/setup/README.md").readText()
    return Regex("""^\| ([0-9][-0-9A-D]*) \| [^|]+ \| \[(step[0-9]+/[^\]]+\.md)]""", RegexOption.MULTILINE).findAll(readme)
        .map { it.groupValues[1] to it.groupValues[2] }
        .toList()
}
