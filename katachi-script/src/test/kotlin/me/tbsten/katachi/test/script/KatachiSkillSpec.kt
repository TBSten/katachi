package me.tbsten.katachi.test.script

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The katachi skill's templates and steps agree with each other (KATACHI-329). Since the agent edits the
 * templates' JSON by hand, nothing at run time catches an item id that a step names but the checklist lacks.
 */
class KatachiSkillSpec : FreeSpec({
    val skill = File("../skills/katachi")

    "テンプレートの JSON は ja / en とも読め、同じキーを持つ" {
        for (file in listOf("check-list.html", "project-code-base-report.html")) {
            val id = if (file == "check-list.html") "checklist" else "report"
            val ja = jsonBlock(skill.resolve("assets/ja/$file"), id)
            val en = jsonBlock(skill.resolve("assets/en/$file"), id)
            ja.keys.toList() shouldBe en.keys.toList()
        }
    }

    "手順が done にする項目 id は、チェックリストの項目 id とそろう" {
        val ids = { lang: String ->
            jsonBlock(skill.resolve("assets/$lang/check-list.html"), "checklist")["steps"]!!.jsonArray
                .flatMap { step -> step.jsonObject["items"]!!.jsonArray.map { it.jsonObject["id"]!!.jsonPrimitive.content } }
                .toSet()
        }
        ids("ja") shouldBe ids("en")
        val marked = skill.resolve("references/setup").walkTopDown().filter { it.isFile }
            .flatMap { file -> MARK_DONE.findAll(file.readText()).flatMap { ITEM_ID.findAll(it.value).map { id -> id.value } } }
            .toSet()
        (marked - ids("en")).shouldBeEmpty()
        (ids("en") - marked).shouldBeEmpty()
    }
})

/** "mark `1-2` done", "mark `4-1`, `4-2` and `4-3` done". */
private val MARK_DONE = Regex("""[Mm]ark (`[0-9]-[0-9A-D]`(, | and )?)+ done""")
private val ITEM_ID = Regex("""[0-9]-[0-9A-D]""")

private fun jsonBlock(file: File, id: String): JsonObject {
    val lines = file.readLines()
    val start = lines.indexOf("""<script type="application/json" id="$id">""")
    val end = lines.subList(start, lines.size).indexOf("</script>") + start
    return Json.parseToJsonElement(lines.subList(start + 1, end).joinToString("\n")).jsonObject
}
