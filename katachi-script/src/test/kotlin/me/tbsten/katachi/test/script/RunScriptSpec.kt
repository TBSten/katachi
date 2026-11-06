package me.tbsten.katachi.test.script

import io.kotest.core.spec.style.FreeSpec
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
        run.out shouldContain "katachi init"
        run.out shouldContain "katachi scaffold --package"
        run.out shouldNotContain "katachi-install.sh"
        run.out shouldNotContain "sh <CLI>"
    }

    "サブコマンドの --help は値として読まずに使い方を出す" {
        val run = runCapturing(createTempDirectory("katachi-script-"), listOf("data", "get", "--help"))
        run.status shouldBe 0
        run.out shouldContain "katachi data get"
    }

    "知らないサブコマンドは止まる" {
        val run = runCapturing(createTempDirectory("katachi-script-"), listOf("nothing"))
        run.status shouldBe 1
        run.err shouldContain "[x] 知らないサブコマンドです: nothing"
    }

    "使い方には spec にあるオプションがすべて載っている（ja / en）" {
        for (lang in Lang.entries) {
            val context = ScriptContext(createTempDirectory("katachi-script-"), emptyMap(), {}, {}).also { it.lang = lang }
            val text = context.usage()
            val missing = COMMAND_SPECS.flatMap { spec -> (spec.options + spec.subcommands.flatMap { it.options }).map { spec.name to it.name } }
                .filter { (command, option) -> text.lines().none { it.contains("katachi $command") && it.contains(option) } && !text.contains("      $option") }
            missing.shouldBeEmpty()
        }
    }

    "guide は init の前でも同梱の手順書から目次を出す" {
        val run = runCapturing(createTempDirectory("katachi-script-"), listOf("guide", "--lang", "en"))
        run.status shouldBe 0
        run.out shouldContain "0-2\tRun init"
    }

    "guide <セクション> の次の案内は katachi guide の形" {
        val run = runCapturing(createTempDirectory("katachi-script-"), listOf("guide", "1-1"))
        run.status shouldBe 0
        run.out.trimEnd().lines().last() shouldBe "次: katachi guide 1-2"
        run.out shouldNotContain "sh \$CLI"
    }

    "init --offline はネットワークなしで、同梱のテンプレートと手順書を置く" {
        val project = createTempDirectory("katachi-project-")
        project.resolve("settings.gradle.kts").writeText("rootProject.name = \"p\"\n")
        project.resolve("build.gradle.kts").writeText("plugins {\n    kotlin(\"jvm\") version \"2.4.10\"\n}\n")
        val run = runCapturing(project, listOf("init", "--offline", "--katachi", "0.4.0", "--lang", "ja", "--workdir", ".local/install-katachi"))
        run.status shouldBe 0
        val workdir = project.resolve(".local/install-katachi")
        workdir.resolve("check-list.html").readText() shouldContain "\"version\": \"0.4.0\""
        workdir.resolve("check-list.html").readText() shouldNotContain "sh katachi-install.sh "
        workdir.resolve("project-code-base-report.html").exists() shouldBe true
        workdir.resolve("tmp/cache/index.md").readText() shouldContain "katachi check 0-1 0-2"
        workdir.resolve("tmp/cache/lang").readText() shouldBe "ja"
        workdir.resolve("tmp/katachi-install.sh").exists() shouldBe false
        run.out shouldContain "KATACHI_CLI=katachi"
        run.out shouldContain "KATACHI_KOTLIN=2.4.10"
        run.out.trimEnd().lines().last() shouldStartWith "次: katachi check 0-1 0-2"
    }

    "init のあとは同じディレクトリから作業用ディレクトリを見つけ、記録した言語で答える" {
        val project = createTempDirectory("katachi-project-")
        project.resolve("settings.gradle.kts").writeText("rootProject.name = \"p\"\n")
        runCapturing(project, listOf("init", "--offline", "--katachi", "0.4.0", "--lang", "en", "--workdir", ".local/install-katachi")).status shouldBe 0
        val check = runCapturing(project, listOf("check", "0-1", "0-2"))
        check.status shouldBe 0
        check.out shouldContain "Marked as done: 0-1, 0-2"
        check.out shouldContain "Next: katachi guide 0-3"
        val verify = runCapturing(project, listOf("verify"))
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
