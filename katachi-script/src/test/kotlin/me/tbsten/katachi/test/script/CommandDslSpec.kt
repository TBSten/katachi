package me.tbsten.katachi.test.script

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import me.tbsten.katachi.script.CommandRunner
import me.tbsten.katachi.script.Katachi
import me.tbsten.katachi.script.KatachiMissingCommandOptionException
import me.tbsten.katachi.script.catalog
import me.tbsten.katachi.script.commands
import me.tbsten.katachi.script.executeCommands
import me.tbsten.katachi.script.ja
import me.tbsten.katachi.script.junit5
import me.tbsten.katachi.script.kotest

/** Building `katachi` command lines from a script, and running them through a [CommandRunner]. */
class CommandDslSpec : FreeSpec({
    class Recording(private val statuses: List<Int> = emptyList()) : CommandRunner {
        val runs = mutableListOf<Pair<List<String>, Path?>>()

        override fun run(arguments: List<String>, workingDirectory: Path?): Int {
            runs += arguments to workingDirectory
            return statuses.getOrElse(runs.size - 1) { 0 }
        }
    }

    "組み立て" - {
        "メソッド名がそのまま CLI のフラグになり、toString は打つときの 1 行になる" {
            val katachi = Katachi(defaultPackage = "")
            val scaffold = katachi.scaffold
                .`package`("me.tbsten.katachi.sample")
                .`test-framework`(kotest)
            scaffold.arguments shouldBe listOf("scaffold", "--package", "me.tbsten.katachi.sample", "--test-framework", "kotest")
            scaffold.toString() shouldBe "katachi scaffold --package me.tbsten.katachi.sample --test-framework kotest"
        }

        "同じオプションを 2 回呼ぶと後の値で置き換わり、位置は最初のまま" {
            Katachi().scaffold
                .`test-framework`(kotest)
                .`dry-run`()
                .`test-framework`(junit5)
                .toString() shouldBe "katachi scaffold --test-framework junit5 --dry-run"
        }

        "フラグは false で外せる" {
            Katachi().scaffold.force().`no-konsist`().force(false).toString() shouldBe "katachi scaffold --no-konsist"
        }

        "Katachi の既定値が入り、サブコマンド側で上書きできる" {
            val katachi = Katachi(defaultPackage = "com.example", katachiVersion = "0.4.0", moduleDir = "static/katachi", lang = ja)
            katachi.scaffold.toString() shouldBe "katachi scaffold --package com.example --katachi 0.4.0 --module-dir static/katachi"
            katachi.scaffold.`package`("com.other").deps(catalog).toString() shouldBe
                "katachi scaffold --package com.other --katachi 0.4.0 --module-dir static/katachi --deps catalog"
            katachi.init.offline().toString() shouldBe "katachi init --katachi 0.4.0 --module-dir static/katachi --lang ja --offline"
        }

        "空白や引用符を含む値だけシェル向けに引用する" {
            Katachi().init.workdir("my dir/it's").toString() shouldBe "katachi init --workdir 'my dir/it'\\''s'"
        }

        "arg と command は渡したものをそのまま足す" {
            Katachi().scaffold.arg("--new-option", "v").arguments shouldBe listOf("scaffold", "--new-option", "v")
            Katachi().command("data", "get", "check-list").toString() shouldBe "katachi data get check-list"
        }

        "commands の toString は 1 行 1 コマンド" {
            val katachi = Katachi(defaultPackage = "com.example")
            commands(katachi.init.lang(ja), katachi.scaffold).toString() shouldBe
                "katachi init --lang ja\nkatachi scaffold --package com.example"
        }
    }

    "実行" - {
        "runner に引数と作業ディレクトリを渡し、終了コードを返す" {
            val runner = Recording(listOf(3))
            val dir = createTempDirectory("katachi-dsl-")
            val katachi = Katachi(workingDirectory = dir, runner = runner, echo = false)
            katachi.scaffold.`package`("com.example").execute() shouldBe 3
            runner.runs shouldBe listOf(listOf("scaffold", "--package", "com.example") to dir)
        }

        "executeCommands は最初に失敗したところで止まる" {
            val runner = Recording(listOf(0, 5, 0))
            val katachi = Katachi(defaultPackage = "com.example", runner = runner, echo = false)
            executeCommands(katachi.init, katachi.scaffold, katachi.command("summary")) shouldBe 5
            runner.runs.map { it.first.first() } shouldBe listOf("init", "scaffold")
        }

        "--package の無い scaffold は何も実行せずに止まり、組み立てた行を指す" {
            val runner = Recording()
            val scaffold = Katachi(defaultPackage = "", runner = runner, echo = false).scaffold.`dry-run`()
            val error = shouldThrow<KatachiMissingCommandOptionException> { scaffold.execute() }
            error.option shouldBe "--package"
            error.command shouldBe "katachi scaffold --dry-run"
            error.declaredAt.fileName shouldBe "CommandDslSpec.kt"
            error.message shouldContain "Katachi(defaultPackage = ...)"
            runner.runs shouldBe emptyList()
        }

        "InProcess はこの JVM の katachi-script で実行する" {
            val workdir = workdirWith("""{"meta": {"moduleDir": "architecture-test"}}""")
            val katachi = Katachi(workingDirectory = workdir, echo = false)
            katachi.command("data", "get", "check-list", "meta.moduleDir").execute() shouldBe 0
            katachi.command("data", "get", "check-list", "meta.missing").execute() shouldBe 1
        }
    }
})
