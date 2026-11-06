package me.tbsten.katachi.test.script

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.script.internal.KatachiScriptExitException
import me.tbsten.katachi.script.internal.commandSpec
import me.tbsten.katachi.script.internal.parseArgs

/** The shared parser every subcommand reads its arguments with. */
class CommandSpecsSpec : FreeSpec({
    "値・フラグ・位置引数を読み分ける" {
        val parsed = parseArgs(commandSpec("scaffold"), listOf("--package", "com.example", "--dry-run", "--test-framework", "kotest"))
        parsed.value("--package") shouldBe "com.example"
        parsed.value("--test-framework") shouldBe "kotest"
        parsed.has("--dry-run") shouldBe true
        parsed.has("--force") shouldBe false
    }

    "選択肢の外の値は使える値を言って止まる" {
        val exit = shouldThrow<KatachiScriptExitException> { parseArgs(commandSpec("scaffold"), listOf("--deps", "maven")) }
        exit.ja shouldBe "--deps は catalog か literal です: maven"
    }

    "知らないオプションはサブコマンドの名前つきで止まる" {
        val exit = shouldThrow<KatachiScriptExitException> { parseArgs(commandSpec("init"), listOf("--nothing")) }
        exit.ja shouldBe "init: 知らないオプションです: --nothing"
    }

    "値の要るオプションに値が無ければ止まる" {
        val exit = shouldThrow<KatachiScriptExitException> { parseArgs(commandSpec("init"), listOf("--workdir")) }
        exit.ja shouldBe "--workdir に値がありません。"
    }

    "入れ子のサブコマンドを選び、省略時は既定のものにする" {
        parseArgs(commandSpec("data"), listOf("get", "check-list", "meta")).let {
            it.subcommand shouldBe "get"
            it.positionals shouldBe listOf("check-list", "meta")
        }
        parseArgs(commandSpec("answers"), listOf("--target", "report")).let {
            it.subcommand shouldBe "show"
            it.value("--target") shouldBe "report"
        }
    }

    "値を省ける選択肢は、次が選択肢でなければフラグとして読む" {
        parseArgs(commandSpec("docs"), listOf("--api", "--refresh")).let {
            it.has("--api") shouldBe true
            it.value("--api") shouldBe null
            it.has("--refresh") shouldBe true
        }
        parseArgs(commandSpec("docs"), listOf("--api", "katachi")).value("--api") shouldBe "katachi"
    }

    "-- の後ろはそのまま渡す" {
        val parsed = parseArgs(commandSpec("test"), listOf("--save", "x", "--", "--info", "-Dkatachi.maxViolations=3"))
        parsed.value("--save") shouldBe "x"
        parsed.passthrough shouldBe listOf("--info", "-Dkatachi.maxViolations=3")
    }

    "rawPositionals のサブコマンドは - で始まる値も位置引数にする" {
        parseArgs(commandSpec("warn"), listOf("4", "--clear")).positionals shouldBe listOf("4", "--clear")
    }
})
