package me.tbsten.katachi.test.script

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.readText
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** `add <kind> --<field> <value>...`: appends one row to the check list or the report (KATACHI-266). */
class AddCommandSpec : FreeSpec({
    "violation をチェックリストに 1 件追記し、渡さなかった項目は null にする" {
        val fixture = AddFixture()

        val run = fixture.run("agent", "add", "violation", "--violation", "[UnexpectedFile] a.kt", "--location", "app/a.kt")

        run.status shouldBe 0
        run.out shouldContain "violations に1件追記しました（計 1 件）"
        val row = fixture.checkList()["violations"]!!.jsonArray.single().jsonObject
        row["violation"] shouldBe JsonPrimitive("[UnexpectedFile] a.kt")
        row["location"] shouldBe JsonPrimitive("app/a.kt")
        row["suggestion"].toString() shouldBe "null"
    }

    "question には q1, q2 と id を振り、--options は何度でも渡せる" {
        val fixture = AddFixture()

        fixture.run("agent", "add", "question", "--question", "A?").out shouldContain "id=q1"
        val second = fixture.run("agent", "add", "question", "--question", "B?", "--options", "yes", "--options", "no")

        second.out shouldContain "id=q2"
        val rows = fixture.checkList()["questions"]!!.jsonArray
        rows.map { it.jsonObject["id"]!!.jsonPrimitive.content } shouldBe listOf("q1", "q2")
        rows[1].jsonObject["options"] shouldBe JsonArray(listOf(JsonPrimitive("yes"), JsonPrimitive("no")))
    }

    "codebase-question はレポートに cq1 として書く" {
        val fixture = AddFixture()

        fixture.run("agent", "add", "codebase-question", "--question", "Which naming?").out shouldContain "id=cq1"

        fixture.report()["questions"]!!.jsonArray.single().jsonObject["id"] shouldBe JsonPrimitive("cq1")
        fixture.checkList()["questions"]!!.jsonArray.size shouldBe 0
    }

    "role の importance は数値にし、数値でなければ何も書かずに止まる" {
        val fixture = AddFixture()

        fixture.run("agent", "add", "role", "--name", "Repository", "--importance", " 3 ", "--group", "data").status shouldBe 0
        fixture.report()["roles"]!!.jsonArray.single().jsonObject["importance"] shouldBe JsonPrimitive(3)

        val run = fixture.run("agent", "add", "role", "--name", "Mapper", "--importance", "high")
        run.status shouldBe 1
        run.err shouldContain "--importance は数値で指定してください: high"
        fixture.report()["roles"]!!.jsonArray.size shouldBe 1
    }

    "Gradle のビルドファイルを layout にした role は止まる（buildSrc と制約だけの Role は通す）" {
        val fixture = AddFixture()

        val gradle = fixture.run("agent", "add", "role", "--name", "Build", "--layout", "app/build.gradle.kts")
        gradle.status shouldBe 1
        gradle.err shouldContain "Gradle のビルドファイルは gradle() が受け持ちます。Role 候補には書きません: app/build.gradle.kts"

        fixture.run("agent", "add", "role", "--name", "Convention", "--layout", "buildSrc/build.gradle.kts").status shouldBe 0

        val noNote = fixture.run("agent", "add", "role", "--name", "Catalog", "--layout", "gradle/libs.versions.toml", "--constraint-only")
        noNote.status shouldBe 1
        noNote.err shouldContain "--constraint-only には --note で理由（何を検査するか）を書いてください"

        fixture.run("agent", "add", "role", "--name", "Catalog", "--layout", "gradle/libs.versions.toml", "--constraint-only", "--note", "versions").status shouldBe 0
        val catalog = fixture.report()["roles"]!!.jsonArray.last().jsonObject
        catalog["constraintOnly"] shouldBe JsonPrimitive(true)
    }

    "同じ path の module は二重に書かずに止まり、changed は記録済みと言って 0 で返す" {
        val fixture = AddFixture()
        fixture.run("agent", "add", "module", "--path", "app", "--kind", "android").status shouldBe 0

        val module = fixture.run("agent", "add", "module", "--path", "app", "--kind", "jvm")
        module.status shouldBe 1
        module.err shouldContain "その path はすでに登録されています: app"
        fixture.report()["modules"]!!.jsonArray.size shouldBe 1

        fixture.run("agent", "add", "changed", "--path", "build.gradle.kts", "--change", "edited").status shouldBe 0
        val changed = fixture.run("agent", "add", "changed", "--path", "build.gradle.kts", "--change", "edited")
        changed.status shouldBe 0
        changed.err shouldContain "その path は記録済みです: build.gradle.kts"
        fixture.checkList()["changedFiles"]!!.jsonArray.size shouldBe 1
    }

    "その種類に無い項目は使える項目を挙げて止まり、module の --role には --responsibility を案内する" {
        val fixture = AddFixture()

        val run = fixture.run("agent", "add", "module", "--path", "app", "--role", "ui")

        run.status shouldBe 1
        run.err shouldContain "--role は module で使えません"
        run.err shouldContain "モジュールの受け持ちは --responsibility です。"
        run.err shouldContain "使えるもの: path, kind, responsibility, buildFile, deviation"
    }

    "項目が 1 つも無ければ空の行を作らずに止まる" {
        val fixture = AddFixture()
        val run = fixture.run("agent", "add", "tool")
        run.status shouldBe 1
        run.err shouldContain "項目を1つも指定していません: add tool"
        fixture.report()["tools"]!!.jsonArray.size shouldBe 0
    }

    "値の無い項目と知らない種類は止まる" {
        val fixture = AddFixture()
        fixture.run("agent", "add", "excluded", "--path").err shouldContain "--path に値がありません"
        fixture.run("agent", "add", "nothing", "--x", "y").err shouldContain "add: 知らない種類です: nothing"
        fixture.run("agent", "add").err shouldContain "add: 種類を指定してください"
    }

    "add <種類> --help は init の前でも項目の一覧を出す" {
        val run = runCapturing(createTempDirectory("katachi-script-"), listOf("agent", "add", "question", "--help"))
        run.status shouldBe 0
        run.out shouldContain "使える項目: --question, --observed, --recommendation, --options"
        run.out shouldContain "id は q1, q2, ... と自動で採番されます"
    }

    "作業用ディレクトリが無ければ init を案内して止まる" {
        val run = runCapturing(createTempDirectory("katachi-script-"), listOf("agent", "add", "violation", "--violation", "x"))
        run.status shouldBe 1
        run.err shouldContain "先に 'katachi agent init' を実行してください"
    }

    "init が作ったテンプレートにそのまま書ける" {
        val project = initializedProject()

        val run = runCapturing(project, listOf("agent", "add", "tool", "--name", "detekt", "--configPath", "config/detekt.yml"))

        run.status shouldBe 0
        run.out shouldContain "tools に1件追記しました（計 1 件）"
        project.installWorkdir.resolve("project-code-base-report.html").readText() shouldContain "\"configPath\": \"config/detekt.yml\""
    }
})

/** A working directory holding an empty check list and an empty report, as `init` leaves them. */
private class AddFixture {
    val workdir: Path = workdirWith(
        """{"meta": {"projectRoot": null}, "steps": [], "violations": [], "questions": [], "answers": {}, "changedFiles": []}""",
    ).also {
        it.writeReport(
            """{"meta": {}, "modules": [], "roles": [], "tools": [], "excluded": [], "questions": [], "answers": {}, "templates": []}""",
        )
    }

    fun run(vararg args: String): RecordedRun = runCapturing(workdir, args.toList(), env = mapOf("KATACHI_WORKDIR" to workdir.toString()))

    fun checkList() = jsonBlockOf(workdir.resolve("check-list.html"), "checklist")

    fun report() = jsonBlockOf(workdir.resolve("project-code-base-report.html"), "report")
}
