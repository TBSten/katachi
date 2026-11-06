package me.tbsten.katachi.test.script

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.nio.file.Path
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** uncheck / warn / verify: the commands that move the check list forward, next to `check` (KATACHI-266). */
class ProgressCommandSpec : FreeSpec({
    "uncheck" - {
        "完了を未完了に戻す" {
            val project = initializedProject()
            runCapturing(project, listOf("check", "1-1")).status shouldBe 0

            val run = runCapturing(project, listOf("uncheck", "1-1"))

            run.status shouldBe 0
            run.out shouldContain "未完了に戻しました: 1-1"
            project.checkListItem("1-1")["done"] shouldBe JsonPrimitive(false)
        }

        "知らない id は使える id を挙げて止まり、何も変えない" {
            val project = initializedProject()
            runCapturing(project, listOf("check", "1-1"))

            val run = runCapturing(project, listOf("uncheck", "1-1", "9-9"))

            run.status shouldBe 1
            run.err shouldContain "知らない項目 id です: 9-9"
            run.err shouldContain "使える id: 0-1, 0-2, 1-1"
            project.checkListItem("1-1")["done"] shouldBe JsonPrimitive(true)
        }

        "id が無ければ止まる" {
            val run = runCapturing(initializedProject(), listOf("uncheck"))
            run.status shouldBe 1
            run.err shouldContain "uncheck: 項目 id を1つ以上指定してください。"
        }
    }

    "warn" - {
        "ステップに警告を付け、--clear で消す" {
            val project = initializedProject()

            runCapturing(project, listOf("warn", "4", "違反を2件残しました")).out shouldContain "ステップ 4 の警告を設定しました"
            project.checkListStep("4")["warning"] shouldBe JsonPrimitive("違反を2件残しました")

            runCapturing(project, listOf("warn", "4", "--clear")).out shouldContain "ステップ 4 の警告を消しました"
            project.checkListStep("4")["warning"] shouldBe JsonNull
        }

        "知らないステップは使える id を挙げて止まる" {
            val run = runCapturing(initializedProject(), listOf("warn", "9", "x"))
            run.status shouldBe 1
            run.err shouldContain "知らないステップ id です: 9\n使える id: 0, 1, 2, 3, 4, 5, 6"
        }

        "文言もステップも無ければ止まる" {
            val project = initializedProject()
            runCapturing(project, listOf("warn", "4")).err shouldContain "warn: 警告の文言を指定してください（消すなら --clear）"
            runCapturing(project, listOf("warn")).err shouldContain "warn: ステップ id を指定してください"
        }
    }

    "verify" - {
        "未完了も未記入も無ければ、最後の必須ステップを完了にして 0" {
            val workdir = workdirWith(verifiable(lastDone = false))

            val run = runCapturing(workdir, listOf("verify"), env = mapOf("KATACHI_WORKDIR" to workdir.toString()))

            run.status shouldBe 0
            run.out shouldContain "未完了・未記入はありません。ステップ 5 を完了にしました。"
            workdir.checkListStep("5")["items"]!!.jsonArray.single().jsonObject["done"] shouldBe JsonPrimitive(true)
            // The optional step is not touched.
            workdir.checkListStep("6")["items"]!!.jsonArray.single().jsonObject["done"] shouldBe JsonPrimitive(false)
        }

        "未チェックの項目と空の meta を挙げて 1" {
            val workdir = workdirWith(verifiable(lastDone = false).replace("\"done\": true", "\"done\": false").replace("\"git\": \"yes\"", "\"git\": \"\""))

            val run = runCapturing(workdir, listOf("verify"), env = mapOf("KATACHI_WORKDIR" to workdir.toString()))

            run.status shouldBe 1
            run.out shouldContain "未完了・未記入が 2 件あります。"
            run.out shouldContain "  - check-list: meta.git"
            run.out shouldContain "  - check-list: 未チェック 1-1 Read the guide"
            run.out shouldNotContain "未チェック 5-1"
        }

        "レポートの未記入を挙げ、警告つきのステップも出す" {
            val workdir = workdirWith(verifiable(lastDone = false).replace("\"id\": \"1\",", "\"id\": \"1\", \"warning\": \"違反を残した\","))
            workdir.writeReport(
                """
                {"meta": {"projectName": "p"}, "overview": {"summary": ""},
                 "architecture": {"name": "layered", "rationale": "because"},
                 "modules": [{"path": "app"}], "directoryTree": "app/", "roles": [{"name": "Repository", "layout": "data/*Repository.kt"}]}
                """.trimIndent(),
            )

            val run = runCapturing(workdir, listOf("verify"), env = mapOf("KATACHI_WORKDIR" to workdir.toString()))

            run.status shouldBe 1
            run.out shouldContain "  - report: overview.summary"
            run.out shouldContain "  - report: roles[0].importance"
            run.out shouldContain "  - report: roles[0].naming"
            run.out shouldContain "  - report: roles[0].group"
            run.out shouldNotContain "report: architecture"
            run.out shouldContain "警告つきのステップ:\n  - ステップ 1: 違反を残した"
        }

        "init の直後は未チェックの項目が並ぶ" {
            val run = runCapturing(initializedProject(), listOf("verify"))
            run.status shouldBe 1
            run.out shouldContain "check-list: 未チェック 0-1"
        }
    }
})

/** A check list whose required steps are done except the last (5), which `verify` itself completes. */
private fun verifiable(lastDone: Boolean): String = """
    {"meta": {"workdir": "w", "projectRoot": "/p", "git": "yes"},
     "steps": [
       {"id": "1", "items": [{"id": "1-1", "label": "Read the guide", "done": true}]},
       {"id": "5", "items": [{"id": "5-1", "label": "Pass verify", "done": $lastDone}]},
       {"id": "6", "optional": true, "items": [{"id": "6-1", "label": "Try it", "done": false}]}
     ]}
""".trimIndent()

private fun Path.checkList(): JsonObject {
    val file = if (resolve("check-list.html").toFile().exists()) resolve("check-list.html") else installWorkdir.resolve("check-list.html")
    return jsonBlockOf(file, "checklist")
}

private fun Path.checkListStep(id: String): JsonObject =
    checkList()["steps"]!!.jsonArray.map { it.jsonObject }.single { it["id"]!!.jsonPrimitive.content == id }

private fun Path.checkListItem(id: String): JsonObject =
    checkList()["steps"]!!.jsonArray.flatMap { it.jsonObject["items"]!!.jsonArray }.map { it.jsonObject }.single { it["id"]!!.jsonPrimitive.content == id }
