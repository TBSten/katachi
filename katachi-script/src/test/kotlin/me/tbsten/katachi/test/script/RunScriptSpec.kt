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

    "--help は人が打つコマンドだけを並べる" {
        val run = runCapturing(createTempDirectory("katachi-script-"), listOf("--help"))
        run.out shouldContain "katachi doctor"
        run.out shouldContain "katachi lint"
        run.out shouldNotContain "katachi agent"
        run.out shouldNotContain "katachi init"
    }

    "消した検査のコマンドを打つと、gradlew を直接打つよう案内して止まる（KATACHI-429）" {
        for (args in listOf(
            listOf("agent"),
            listOf("agent", "--help"),
            listOf("agent", "test"),
            listOf("agent", "test", "--save", "x", "--", "-Dx=1"),
            listOf("agent", "compare-violations"),
            listOf("test"),
            listOf("compare-violations"),
        )) {
            val run = runCapturing(createTempDirectory("katachi-script-"), args)
            run.status shouldBe 1
            run.err shouldContain "は無くなりました。検査は ./gradlew <検査用モジュール>:test を直接実行します"
            run.err shouldContain "gh skill install TBSten/katachi katachi"
        }
    }

    "消したコマンドの名前は、打った形のまま案内に出す" {
        runCapturing(createTempDirectory("katachi-script-"), listOf("agent", "test")).err shouldContain "katachi agent test は無くなりました"
        runCapturing(createTempDirectory("katachi-script-"), listOf("agent")).err shouldContain "katachi agent は無くなりました"
        runCapturing(createTempDirectory("katachi-script-"), listOf("test")).err shouldContain "katachi test は無くなりました"
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
        val run = runCapturing(workdir, listOf("agent", "test"))
        run.status shouldBe 1
        run.err shouldContain "was removed. Run the check with ./gradlew <verification module>:test directly"
    }

    "サブコマンドの --help は値として読まずに使い方を出す" {
        for (command in listOf("lint", "doctor", "scaffold")) {
            val run = runCapturing(createTempDirectory("katachi-script-"), listOf(command, "--help"))
            run.status shouldBe 0
            run.out shouldContain "katachi $command"
        }
    }

    "知らないサブコマンドは止まる" {
        val run = runCapturing(createTempDirectory("katachi-script-"), listOf("nothing"))
        run.status shouldBe 2
        run.err shouldContain "[x] 知らないサブコマンドです: nothing"
    }

    "サブコマンドの --help は、そのコマンドの分だけを出す（KATACHI-418）" {
        val scaffold = runCapturing(createTempDirectory("katachi-script-"), listOf("scaffold", "--help"))
        scaffold.status shouldBe 0
        scaffold.out shouldStartWith "katachi scaffold --package"
        scaffold.out shouldContain "--test-framework"
        scaffold.out shouldNotContain "katachi doctor"
        scaffold.out shouldNotContain "katachi lint"
        scaffold.out shouldNotContain "katachi --version"

        val doctor = runCapturing(createTempDirectory("katachi-script-"), listOf("doctor", "--help"), env = mapOf("KATACHI_LANG" to "en"))
        doctor.out shouldStartWith "katachi doctor"
        doctor.out shouldContain "Checks the JDK"
        doctor.out shouldNotContain "--package"

        // the top-level --help still lists everything
        runCapturing(createTempDirectory("katachi-script-"), listOf("--help")).out.let {
            it shouldContain "katachi doctor"
            it shouldContain "katachi lint"
        }
    }

    "KATACHI_LANG は EN や en_US.UTF-8 でも読む。読めない値は警告して続ける（KATACHI-418）" {
        for (value in listOf("EN", "en_US.UTF-8", " en ")) {
            val run = runCapturing(createTempDirectory("katachi-script-"), listOf("nothing"), env = mapOf("KATACHI_LANG" to value))
            run.err shouldContain "Unknown subcommand: nothing"
            run.err shouldNotContain "KATACHI_LANG"
        }
        val ja = runCapturing(createTempDirectory("katachi-script-"), listOf("nothing"), env = mapOf("KATACHI_LANG" to "JA_JP"))
        ja.err shouldContain "知らないサブコマンドです: nothing"

        val bad = runCapturing(createTempDirectory("katachi-script-"), listOf("nothing"), env = mapOf("KATACHI_LANG" to "fr"))
        bad.status shouldBe 2
        bad.err shouldContain "KATACHI_LANG=fr は読めません"
        bad.err shouldContain "知らないサブコマンドです: nothing"

        val badEn = runCapturing(createTempDirectory("katachi-script-"), listOf("nothing"), env = mapOf("KATACHI_LANG" to "fr", "LANG" to "en_US.UTF-8"))
        badEn.err shouldContain "KATACHI_LANG=fr is not readable (use ja or en). Printing in en."
        badEn.err shouldContain "Unknown subcommand: nothing"
    }

    "名前が .katachi.kts で終わらない .kts は、名前の決まりを教える（KATACHI-418）" {
        val run = runCapturing(createTempDirectory("katachi-script-"), listOf("setup.kts"), env = mapOf("KATACHI_LANG" to "en"))
        run.status shouldBe 2
        run.err shouldContain "setup.kts cannot run as a script"
        run.err shouldContain ".katachi.kts"
        runCapturing(createTempDirectory("katachi-script-"), listOf("setup.kts")).err shouldContain "ファイル名は .katachi.kts で終わる"
    }

    "使い方には spec にあるオプションがすべて載っている（ja / en）" {
        for (lang in Lang.entries) {
            val context = ScriptContext(createTempDirectory("katachi-script-"), emptyMap(), {}, {}).also { it.lang = lang }
            val text = context.usage()
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
