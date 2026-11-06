package me.tbsten.katachi.test.script

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/** `answers show / set / import`: the user's answers to the questions of the check list and the report (KATACHI-266). */
class AnswersCommandSpec : FreeSpec({
    "show は両方のファイルの未回答だけを推奨つきで出す" {
        val fixture = AnswersFixture()

        val run = fixture.run("answers")

        run.status shouldBe 0
        run.out shouldContain "対象: check-list\n回答済み 1 件 / 未回答 1 件\n- q2 テストは JUnit5 にしますか → 推奨: はい"
        run.out shouldNotContain "- q1 "
        run.out shouldContain "対象: report\n回答済み 0 件 / 未回答 1 件\n- cq1 命名は Impl と Default のどちらですか"
    }

    "show --target で片方だけにできる" {
        val run = AnswersFixture().run("answers", "show", "--target", "report")
        run.out shouldContain "対象: report"
        run.out shouldNotContain "対象: check-list"
    }

    "set は id から書く先を決め、回答と注記を記録する" {
        val fixture = AnswersFixture()

        val run = fixture.run("answers", "set", "q2", "いいえ", "--note", "kotest を使っている")

        run.status shouldBe 0
        run.out shouldContain "対象: check-list"
        run.out shouldContain "取り込みました: 回答 1 件（未回答 0 件）"
        val answer = fixture.block(CHECK_LIST)["answers"]!!.jsonObject["q2"]!!.jsonObject
        answer["answer"] shouldBe JsonPrimitive("いいえ")
        answer["note"] shouldBe JsonPrimitive("kotest を使っている")
        // The answer recorded earlier stays.
        fixture.block(CHECK_LIST)["answers"]!!.jsonObject.keys shouldBe setOf("q1", "q2")
    }

    "set で --target と id の書く先が食い違えば何も書かない" {
        val fixture = AnswersFixture()

        val run = fixture.run("answers", "set", "cq1", "Impl", "--target", "check-list")

        run.status shouldBe 1
        run.err shouldContain "cq1 はレポート（report）の確認事項で、--target check-list と食い違うので書きませんでした。"
        fixture.block(REPORT)["answers"]!!.jsonObject.size shouldBe 0
    }

    "set は知らない id と、確認事項の形でない id を止める" {
        val fixture = AnswersFixture()
        fixture.run("answers", "set", "q9", "x").err shouldContain "知らない id です: q9（使える id: q1, q2）"
        fixture.run("answers", "set", "x1", "x").err shouldContain "answers set: 確認事項の id ではありません: x1"
        fixture.run("answers", "set", "q1").err shouldContain "answers set: <id> <回答> を指定してください"
        fixture.run("answers", "set", "q1", "a", "b").err shouldContain "answers: 引数が多すぎます: b"
    }

    "import はブラウザで書き出した JSON を取り込み、空の回答と他方の id は飛ばす" {
        val fixture = AnswersFixture()
        val json = fixture.workdir.resolve("answers.json")
        json.writeText(
            """
            {"target": "report", "answers": {
              "cq1": {"answer": "Impl", "note": "", "answeredAt": "2026-11-01T10:00:00+09:00"},
              "q2": {"answer": "はい"},
              "cq7": {"answer": "?"}
            }}
            """.trimIndent(),
        )

        val run = fixture.run("answers", "import", json.toString())

        run.status shouldBe 0
        run.out shouldContain "取り込みました: 回答 1 件（未回答 0 件）"
        run.err shouldContain "[!] q2 はチェックリストの確認事項なので、ここには入れずに飛ばしました"
        run.err shouldContain "[!] 知らない id を飛ばしました: cq7"
        val answer = fixture.block(REPORT)["answers"]!!.jsonObject["cq1"]!!.jsonObject
        answer["answer"] shouldBe JsonPrimitive("Impl")
        answer["note"] shouldBe JsonNull
        answer["answeredAt"] shouldBe JsonPrimitive("2026-11-01T10:00:00+09:00")
    }

    "import は target の無い JSON の書く先を id から決め、決められなければ止まる" {
        val fixture = AnswersFixture()
        val fromIds = fixture.workdir.resolve("q.json").also { it.writeText("""{"answers": {"q2": "はい"}}""") }
        fixture.run("answers", "import", fromIds.toString()).out shouldContain "対象: check-list"

        val mixed = fixture.workdir.resolve("mixed.json").also { it.writeText("""{"answers": {"q2": "a", "cq1": "b"}}""") }
        val run = fixture.run("answers", "import", mixed.toString())
        run.status shouldBe 1
        run.err shouldContain "--target report か --target check-list を付けてください。"
    }

    "import は JSON として読めないファイルを止める" {
        val fixture = AnswersFixture()
        val broken = fixture.workdir.resolve("broken.json").also { it.writeText("{\"answers\": ") }

        val run = fixture.run("answers", "import", broken.toString(), "--target", "report")

        run.status shouldBe 1
        run.err shouldContain "JSON として読めません: "
    }

    "import はファイルが無ければ止まる" {
        val run = AnswersFixture().run("answers", "import", "nowhere.json")
        run.status shouldBe 1
        run.err shouldContain "ファイルがありません: "
    }
})

private const val CHECK_LIST = "check-list.html"
private const val REPORT = "project-code-base-report.html"

/** Two questions in the check list (q1 answered) and one in the report. */
private class AnswersFixture {
    val workdir: Path = workdirWith(
        """
        {"meta": {}, "steps": [],
         "questions": [
           {"id": "q1", "question": "Kotlin の版は 2.4 で良いですか"},
           {"id": "q2", "question": "テストは JUnit5 にしますか", "recommendation": "はい"}
         ],
         "answers": {"q1": {"answer": "はい", "note": null, "answeredAt": "2026-11-01T00:00:00Z"}}}
        """.trimIndent(),
    ).also {
        it.writeReport("""{"meta": {}, "questions": [{"id": "cq1", "question": "命名は Impl と Default のどちらですか"}], "answers": {}}""")
    }

    fun run(vararg args: String): RecordedRun = runCapturing(workdir, args.toList(), env = mapOf("KATACHI_WORKDIR" to workdir.toString()))

    fun block(file: String) = jsonBlockOf(workdir.resolve(file), if (file == CHECK_LIST) "checklist" else "report")
}
