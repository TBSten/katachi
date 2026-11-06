package me.tbsten.katachi.test.script

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory

/** `summary`: the message handed back to the user at the end, or where to resume (KATACHI-266). */
class SummaryCommandSpec : FreeSpec({
    "終わっていなければ、未完了の項目と再開の手がかりを出す" {
        val project = initializedProject()
        runCapturing(project, listOf("check", "0-1", "0-2")).status shouldBe 0

        val run = runCapturing(project, listOf("summary"))

        run.status shouldBe 0
        run.out shouldContain "❌ katachi のセットアップができませんでした"
        run.out shouldContain "終わっていない項目:\n- 1-1 "
        run.out shouldContain "  終わった項目: 0-1 0-2"
        run.out shouldContain "  次のセクション: katachi guide 0-3"
        run.out shouldContain "  定義: まだ無い（scaffold 未実行。meta.moduleDir = architecture-test）"
        run.out shouldNotContain "- 6-1 "
    }

    "違反を残して終わったなら ⚠️ で、未回答の確認事項とテンプレートの提案を出す" {
        val fixture = SummaryFixture(violations = """[{"violation": "[UnexpectedFile] a.kt"}]""")
        fixture.workdir.writeReport(
            """{"questions": [], "answers": {}, "templates": [{"role": "UseCase", "files": ["UseCase.kt"], "params": ["name"]}]}""",
        )

        val run = fixture.run()

        run.status shouldBe 0
        run.out shouldContain "⚠️ katachi のセットアップを行いました"
        run.out shouldContain "- `:architecture-test:test` の実行結果: ⚠️ 1 件のアーキテクチャ違反を残しています"
        run.out shouldContain "- 確認したいこと（チェックリスト）: 回答済み 0 件 / 未回答 1 件（チェックリストの「ユーザに確認したいこと」）\n    - q1 JUnit5 にしますか → 推奨: はい"
        run.out shouldContain "    - CI に `:architecture-test:test` を組み込んでください"
        run.out shouldContain "    - テンプレートからのコード生成をセットアップできます。提案は次の 1 件です"
        run.out shouldContain "        - UseCase: UseCase.kt（パラメータ: name）"
        run.out shouldContain "    - テンプレートからのコード生成をセットアップする: katachi guide 6-D"
    }

    "違反も提案も無ければ ✅ で、テンプレートは勧めない" {
        val fixture = SummaryFixture(violations = "[]")

        val run = fixture.run()

        run.out shouldContain "✅ katachi のセットアップを行いました"
        run.out shouldContain "- `:architecture-test:test` の実行結果: ✅ All green"
        run.out shouldContain "    - レビューの指摘を直す: katachi guide 6-0"
        run.out shouldContain "    - CI に組み込む: katachi guide 6-B"
        run.out shouldNotContain "テンプレート"
    }

    "済んだ任意の項目は次の行動に出さない" {
        val fixture = SummaryFixture(violations = "[]", optionalDone = setOf("6-2"))

        val run = fixture.run()

        run.out shouldNotContain "CI に"
        run.out shouldContain "  終わった項目: 1-1 5-1 6-2"
    }
})

/** A check list with every required item done, the optional step 6 open, and one unanswered question. */
private class SummaryFixture(violations: String, optionalDone: Set<String> = emptySet()) {
    val project: Path = createTempDirectory("katachi-project-")
    val workdir: Path

    init {
        project.resolve("architecture-test/src/test/kotlin").createDirectories()
        val optional = listOf("6-1", "6-2", "6-3", "6-4").joinToString(", ") { """{"id": "$it", "label": "$it", "done": ${it in optionalDone}}""" }
        workdir = workdirWith(
            """
            {"meta": {"projectRoot": "$project"},
             "steps": [
               {"id": "1", "items": [{"id": "1-1", "done": true}]},
               {"id": "5", "items": [{"id": "5-1", "done": true}]},
               {"id": "6", "optional": true, "items": [$optional]}
             ],
             "violations": $violations, "changedFiles": [],
             "questions": [{"id": "q1", "question": "JUnit5 にしますか", "recommendation": "はい"}], "answers": {}}
            """.trimIndent(),
        )
    }

    fun run(): RecordedRun = runCapturing(project, listOf("summary"), env = mapOf("KATACHI_WORKDIR" to workdir.toString()))
}
