package me.tbsten.katachi.test.script

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import me.tbsten.katachi.script.Katachi
import me.tbsten.katachi.script.agent
import me.tbsten.katachi.script.catalog
import me.tbsten.katachi.script.command.CommandRunner
import me.tbsten.katachi.script.command.KatachiCommand
import me.tbsten.katachi.script.commands
import me.tbsten.katachi.script.`compare-violations`
import me.tbsten.katachi.script.doctor
import me.tbsten.katachi.script.error.KatachiMissingCommandOptionException
import me.tbsten.katachi.script.executeCommands
import me.tbsten.katachi.script.ja
import me.tbsten.katachi.script.junit5
import me.tbsten.katachi.script.kotest
import me.tbsten.katachi.script.lint
import me.tbsten.katachi.script.scaffold
import me.tbsten.katachi.script.test
import me.tbsten.katachi.script.internal.AGENT_GROUP
import me.tbsten.katachi.script.internal.commandSpec
import me.tbsten.katachi.script.internal.parseArgs

/** Building `katachi` command lines from a script, and running them through a [CommandRunner]. */
class CommandDslSpec : FreeSpec({
    class Recording(private val statuses: List<Int> = emptyList()) : CommandRunner {
        val runs = mutableListOf<Pair<List<String>, Path?>>()

        override fun run(arguments: List<String>, cwd: Path?): Int {
            runs += arguments to cwd
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
            katachi.doctor.toString() shouldBe "katachi doctor --lang ja"
        }

        "cwd はコマンドラインに出ない" {
            Katachi(cwd = Path.of("/somewhere")).scaffold.toString() shouldBe "katachi scaffold"
        }

        "空白や引用符を含む値だけシェル向けに引用する" {
            Katachi().scaffold.`module-dir`("my dir/it's").toString() shouldBe "katachi scaffold --module-dir 'my dir/it'\\''s'"
        }

        "arg と command は渡したものをそのまま足す" {
            Katachi().scaffold.arg("--new-option", "v").arguments shouldBe listOf("scaffold", "--new-option", "v")
            Katachi().command("agent", "new-command", "x").toString() shouldBe "katachi agent new-command x"
        }

        "commands の toString は 1 行 1 コマンド" {
            val katachi = Katachi(defaultPackage = "com.example")
            commands(katachi.doctor.lang(ja), katachi.scaffold).toString() shouldBe
                "katachi doctor --lang ja\nkatachi scaffold --package com.example"
        }
    }

    "scaffold 以外のサブコマンド" - {
        val katachi = Katachi()

        "Katachi の lang は --lang を取るコマンドに入る" {
            Katachi(lang = ja).doctor.toString() shouldBe "katachi doctor --lang ja"
            katachi.doctor.toString() shouldBe "katachi doctor"
        }

        "test の gradle は -- の後ろに足していく" {
            katachi.agent.test.save("before-refactor").gradle("--info").gradle("-Dkatachi.baseline.update=true").toString() shouldBe
                "katachi agent test --save before-refactor -- --info -Dkatachi.baseline.update=true"
        }

        "引数を取らないコマンドと、省ける位置引数だけのコマンド" {
            katachi.lint.toString() shouldBe "katachi lint"
            katachi.agent.`compare-violations`.toString() shouldBe "katachi agent compare-violations"
            katachi.agent.`compare-violations`("a.log", "b.log").toString() shouldBe "katachi agent compare-violations a.log b.log"
        }

        "組み立てた行は CLI の解析をそのまま通る" {
            val built: List<KatachiCommand> = listOf(
                Katachi(defaultPackage = "com.example").scaffold.`test-framework`(kotest).deps(catalog).`dry-run`(),
                Katachi(lang = ja).doctor,
                katachi.agent.test.save("x").gradle("--info"),
                katachi.agent.`compare-violations`("a.log", "b.log"),
                katachi.lint,
            )
            for (command in built) {
                val arguments = command.arguments.dropWhile { it == AGENT_GROUP }
                shouldNotThrowAny { parseArgs(commandSpec(arguments.first()), arguments.drop(1)) }
            }
        }
    }

    "実行" - {
        "runner に引数と cwd を渡し、終了コードを返す" {
            val runner = Recording(listOf(3))
            val dir = createTempDirectory("katachi-dsl-")
            val katachi = Katachi(cwd = dir, runner = runner, echo = false)
            katachi.scaffold.`package`("com.example").execute() shouldBe 3
            runner.runs shouldBe listOf(listOf("scaffold", "--package", "com.example") to dir)
        }

        "executeCommands は最初に失敗したところで止まる" {
            val runner = Recording(listOf(0, 5, 0))
            val katachi = Katachi(defaultPackage = "com.example", runner = runner, echo = false)
            executeCommands(katachi.doctor, katachi.scaffold, katachi.lint) shouldBe 5
            runner.runs.map { it.first.take(2) } shouldBe listOf(listOf("doctor"), listOf("scaffold", "--package"))
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
            val project = createTempDirectory("katachi-dsl-")
            val katachi = Katachi(cwd = project, echo = false)
            // No definition directory yet: lint stops with 1, a command it does not know with 1 too.
            katachi.lint.execute() shouldBe 1
            katachi.command("--version").execute() shouldBe 0
        }
    }
})
