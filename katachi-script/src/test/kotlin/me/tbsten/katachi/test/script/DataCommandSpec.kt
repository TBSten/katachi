package me.tbsten.katachi.test.script

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * `data get / set / merge` against a working directory laid out as `init` leaves it. The
 * expectations are the ones `check-install-kit-smoke.sh` holds the shell script to, so the two
 * implementations can be swapped under the same guide.
 */
class DataCommandSpec : FreeSpec({
    val json = """{"meta": {"moduleDir": "architecture-test", "lang": "ja"}, "questions": [{"id": "q1"}]}"""

    "get" - {
        "キー無しはブロックの中身をそのまま出す" {
            val workdir = workdirWith(json)
            val run = runCapturing(workdir, listOf("data", "get", "check-list"))
            run.status shouldBe 0
            run.out shouldBe json + "\n"
        }

        "文字列のキーは引用符なしで出す" {
            val workdir = workdirWith(json)
            val run = runCapturing(workdir, listOf("data", "get", "check-list", "meta.moduleDir"))
            run.status shouldBe 0
            run.out shouldBe "architecture-test\n"
        }

        "文字列以外のキーは JSON で出し、配列の添字も辿れる" {
            val workdir = workdirWith(json)
            runCapturing(workdir, listOf("data", "get", "check-list", "questions.0")).out shouldBe "{\n  \"id\": \"q1\"\n}\n"
            runCapturing(workdir, listOf("data", "get", "check-list", "meta")).out shouldContain "\"moduleDir\": \"architecture-test\""
        }

        "無いキーは [x] なしのメッセージで 1 を返す" {
            val workdir = workdirWith(json)
            val run = runCapturing(workdir, listOf("data", "get", "check-list", "meta.nothing"))
            run.status shouldBe 1
            run.err shouldBe "キーがありません: meta.nothing\n"
        }

        "作業用ディレクトリは KATACHI_WORKDIR でも指定できる" {
            val workdir = workdirWith(json)
            val elsewhere = workdirWith("{}")
            val run = runCapturing(elsewhere, listOf("data", "get", "check-list", "meta.moduleDir"), env = mapOf("KATACHI_WORKDIR" to workdir.toString()))
            run.out shouldBe "architecture-test\n"
        }

        "作業用ディレクトリが見つからなければ init を案内して止まる" {
            val empty = kotlin.io.path.createTempDirectory("katachi-script-empty-")
            val run = runCapturing(empty, listOf("data", "get", "check-list"))
            run.status shouldBe 1
            run.err shouldContain "[x] 作業用ディレクトリが分かりません"
        }

        "ファイルパスでも指定でき、id はファイル名から決まる" {
            val workdir = workdirWith(json)
            val run = runCapturing(workdir, listOf("data", "get", "check-list.html", "meta.lang"))
            run.out shouldBe "ja\n"
        }

        "init が記録した言語が出力の言語になる" {
            val workdir = workdirWith(json, lang = "en")
            val run = runCapturing(workdir, listOf("data", "get", "check-list", "meta.nothing"))
            run.err shouldBe "No such key: meta.nothing\n"
        }
    }

    "set" - {
        "ブロックを丸ごと差し替え、元を .bak に残す" {
            val workdir = workdirWith(json)
            val patch = workdir.resolve("new.json").apply { writeText("""{"meta": {"moduleDir": "x"}}""" + "\n") }
            val run = runCapturing(workdir, listOf("data", "set", "check-list", patch.toString()))
            run.status shouldBe 0
            run.out shouldContain "を更新しました"
            workdir.resolve("check-list.html.bak").readText() shouldBe checkListHtml(json)
            runCapturing(workdir, listOf("data", "get", "check-list")).out shouldBe """{"meta": {"moduleDir": "x"}}""" + "\n"
            // The page around the block is untouched.
            workdir.resolve("check-list.html").readText() shouldContain "<script>render()</script>"
        }

        "JSON として読めないファイルは何も書かずに止まる" {
            val workdir = workdirWith(json)
            val patch = workdir.resolve("bad.json").apply { writeText("{not json") }
            val run = runCapturing(workdir, listOf("data", "set", "check-list", patch.toString()))
            run.status shouldBe 1
            run.err shouldContain "JSON として読めません"
            workdir.resolve("check-list.html").readText() shouldBe checkListHtml(json)
            workdir.resolve("check-list.html.bak").exists() shouldBe false
        }

        "値の中の </script> は <\\/script> にして書く" {
            val workdir = workdirWith(json)
            val patch = workdir.resolve("new.json").apply { writeText("""{"note": "a</script>b"}""") }
            runCapturing(workdir, listOf("data", "set", "check-list", patch.toString())).status shouldBe 0
            val html = workdir.resolve("check-list.html").readText()
            html shouldContain "a<\\/script>b"
            runCapturing(workdir, listOf("data", "get", "check-list", "note")).out shouldBe "a</script>b\n"
        }

        "ロックの残骸を消してから書く" {
            val workdir = workdirWith(json)
            val lock = workdir.resolve("tmp/cache/.lock").createDirectories()
            lock.resolve("pid").writeText("999999999")
            val patch = workdir.resolve("new.json").apply { writeText("{}") }
            val run = runCapturing(workdir, listOf("data", "set", "check-list", patch.toString()))
            run.status shouldBe 0
            lock.exists() shouldBe false
        }
    }

    "merge" - {
        "辞書は深くマージし、配列は丸ごと置き換える" {
            val workdir = workdirWith(json)
            val patch = workdir.resolve("patch.json").apply { writeText("""{"meta": {"lang": "en"}, "questions": [{"id": "q9"}]}""") }
            runCapturing(workdir, listOf("data", "merge", "check-list", patch.toString())).status shouldBe 0
            val merged = runCapturing(workdir, listOf("data", "get", "check-list")).out
            merged shouldContain "\"moduleDir\": \"architecture-test\""
            merged shouldContain "\"lang\": \"en\""
            merged shouldContain "\"q9\""
            merged shouldNotContain "\"q1\""
        }

        "閉じタグが行頭に無いブロックには書かない" {
            val workdir = workdirWith(json)
            val html = workdir.resolve("check-list.html")
            html.writeText(html.readText().replace("\n</script>\n<script>render()", "</script>\n<script>render()"))
            val patch = workdir.resolve("patch.json").apply { writeText("{}") }
            val run = runCapturing(workdir, listOf("data", "merge", "check-list", patch.toString()))
            run.status shouldBe 1
            run.err shouldContain "行頭に単独で"
        }
    }

    "引数" - {
        "get / set / merge 以外は止まる" {
            val workdir = workdirWith(json)
            val run = runCapturing(workdir, listOf("data", "show", "check-list"))
            run.status shouldBe 1
            run.err shouldContain "data のサブコマンドは get / set / merge です（指定されたのは 'show'）"
        }

        "知らないオプションと余分な引数は止まる" {
            val workdir = workdirWith(json)
            runCapturing(workdir, listOf("data", "get", "check-list", "--nope")).err shouldContain "data: 知らないオプションです: --nope"
            runCapturing(workdir, listOf("data", "get", "check-list", "a", "b")).err shouldContain "data get: 余分な引数です: b"
        }

        "--id で別のブロックを選べる" {
            val workdir = workdirWith(json)
            val run = runCapturing(workdir, listOf("data", "get", "check-list", "--id", "nothing"))
            run.status shouldBe 1
            run.err shouldContain "id=\"nothing\" の JSON ブロックが見つかりません"
        }
    }
})
