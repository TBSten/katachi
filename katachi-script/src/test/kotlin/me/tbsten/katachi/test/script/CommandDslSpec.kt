package me.tbsten.katachi.test.script

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import me.tbsten.katachi.script.ApiModule
import me.tbsten.katachi.script.CommandRunner
import me.tbsten.katachi.script.Katachi
import me.tbsten.katachi.script.KatachiCommand
import me.tbsten.katachi.script.KatachiMissingCommandOptionException
import me.tbsten.katachi.script.add
import me.tbsten.katachi.script.answers
import me.tbsten.katachi.script.catalog
import me.tbsten.katachi.script.check
import me.tbsten.katachi.script.checkList
import me.tbsten.katachi.script.commands
import me.tbsten.katachi.script.`compare-violations`
import me.tbsten.katachi.script.data
import me.tbsten.katachi.script.docs
import me.tbsten.katachi.script.doctor
import me.tbsten.katachi.script.executeCommands
import me.tbsten.katachi.script.guide
import me.tbsten.katachi.script.init
import me.tbsten.katachi.script.ja
import me.tbsten.katachi.script.junit5
import me.tbsten.katachi.script.kotest
import me.tbsten.katachi.script.lint
import me.tbsten.katachi.script.numbered
import me.tbsten.katachi.script.preferences
import me.tbsten.katachi.script.report
import me.tbsten.katachi.script.scaffold
import me.tbsten.katachi.script.summary
import me.tbsten.katachi.script.test
import me.tbsten.katachi.script.uncheck
import me.tbsten.katachi.script.verify
import me.tbsten.katachi.script.warn
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
            katachi.init.offline().toString() shouldBe "katachi init --katachi 0.4.0 --module-dir static/katachi --lang ja --offline"
        }

        "workdir は init の --workdir になり、cwd はコマンドラインに出ない" {
            val katachi = Katachi(workdir = "tmp/katachi", cwd = Path.of("/somewhere"))
            katachi.init.toString() shouldBe "katachi init --workdir tmp/katachi"
            katachi.scaffold.toString() shouldBe "katachi scaffold"
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

    "init・scaffold 以外のサブコマンド" - {
        val katachi = Katachi()

        "Katachi の lang は --lang を取るコマンドすべてに入る" {
            Katachi(lang = ja).doctor.toString() shouldBe "katachi doctor --lang ja"
            Katachi(lang = ja).guide("3-2").refresh().toString() shouldBe "katachi guide 3-2 --lang ja --refresh"
            katachi.guide.toString() shouldBe "katachi guide"
        }

        "入れ子のサブコマンドは位置引数を関数の引数で受ける" {
            katachi.data.get("check-list", "meta.moduleDir").id("checklist").toString() shouldBe
                "katachi data get check-list meta.moduleDir --id checklist"
            katachi.data.get("report").toString() shouldBe "katachi data get report"
            katachi.data.set("check-list", "meta.json").toString() shouldBe "katachi data set check-list meta.json"
            katachi.data.merge("report", "patch.json").id("report").toString() shouldBe "katachi data merge report patch.json --id report"
        }

        "値を省ける選択肢は引数なしでも書ける" {
            katachi.docs.api().toString() shouldBe "katachi docs --api"
            katachi.docs.api(ApiModule.KatachiKonsist).refresh().toString() shouldBe "katachi docs --api katachi-konsist --refresh"
            katachi.docs.topic("layout").small().topics(false).toString() shouldBe "katachi docs --topic layout --small"
        }

        "test の gradle は -- の後ろに足していく" {
            katachi.test.save("before-refactor").gradle("--info").gradle("-Dkatachi.baseline.update=true").toString() shouldBe
                "katachi test --save before-refactor -- --info -Dkatachi.baseline.update=true"
        }

        "check / uncheck / warn は - で始まる値もそのまま渡す" {
            katachi.check("1-1", "1-2").toString() shouldBe "katachi check 1-1 1-2"
            katachi.uncheck("3-2").toString() shouldBe "katachi uncheck 3-2"
            katachi.warn("4", "-5 lines").arguments shouldBe listOf("warn", "4", "-5 lines")
            katachi.warn("4").clear().toString() shouldBe "katachi warn 4 --clear"
        }

        "add は種類ごとの項目をメソッドにし、複数取る項目は足していく" {
            katachi.add.role.name("UseCase").examples("A.kt").examples("B.kt", "C.kt").`constraint-only`().note("x").toString() shouldBe
                "katachi add role --name UseCase --examples A.kt --examples B.kt --examples C.kt --constraint-only --note x"
            katachi.add.`codebase-question`.question("Nest groups?").options("yes", "no").toString() shouldBe
                "katachi add codebase-question --question 'Nest groups?' --options yes --options no"
            katachi.add.violation.whyNotFixed("legacy").toString() shouldBe "katachi add violation --whyNotFixed legacy"
        }

        "preferences set は key=value にし、yes / no は Boolean で渡す" {
            katachi.preferences.set
                .nestedGroups(false)
                .testFramework(kotest)
                .constraintNames(numbered)
                .nestedGroups(true)
                .toString() shouldBe "katachi preferences set nestedGroups=yes testFramework=kotest constraintNames=numbered"
            katachi.preferences.show.toString() shouldBe "katachi preferences show"
        }

        "answers は show / import / set を選ぶ" {
            katachi.answers.show.target(report).toString() shouldBe "katachi answers show --target report"
            katachi.answers.import("answers.json").target(checkList).toString() shouldBe "katachi answers import answers.json --target check-list"
            katachi.answers.set("cq1", "yes, both").note("agreed").toString() shouldBe "katachi answers set cq1 'yes, both' --note agreed"
        }

        "引数を取らないコマンドと、省ける位置引数だけのコマンド" {
            katachi.lint.toString() shouldBe "katachi lint"
            katachi.verify.toString() shouldBe "katachi verify"
            katachi.summary.toString() shouldBe "katachi summary"
            katachi.`compare-violations`.toString() shouldBe "katachi compare-violations"
            katachi.`compare-violations`("a.log", "b.log").toString() shouldBe "katachi compare-violations a.log b.log"
        }

        "組み立てた行は CLI の解析をそのまま通る" {
            val built: List<KatachiCommand> = listOf(
                Katachi(defaultPackage = "com.example", lang = ja).init.offline().force(),
                Katachi(defaultPackage = "com.example").scaffold.`test-framework`(kotest).deps(catalog).`dry-run`(),
                Katachi(lang = ja).doctor,
                katachi.data.get("check-list", "meta").id("checklist"),
                katachi.docs.api(ApiModule.Katachi).refresh(),
                Katachi(lang = ja).guide("3-2").refresh(),
                katachi.test.save("x").gradle("--info"),
                katachi.answers.set("q1", "yes").note("n").target(checkList),
            )
            for (command in built) {
                shouldNotThrowAny { parseArgs(commandSpec(command.arguments.first()), command.arguments.drop(1)) }
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
            val katachi = Katachi(cwd = workdir, echo = false)
            katachi.data.get("check-list", "meta.moduleDir").execute() shouldBe 0
            katachi.data.get("check-list", "meta.missing").execute() shouldBe 1
            katachi.command("data", "get", "check-list", "meta.moduleDir").execute() shouldBe 0
        }
    }
})
