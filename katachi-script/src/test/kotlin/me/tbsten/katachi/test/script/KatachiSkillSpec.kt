package me.tbsten.katachi.test.script

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.tbsten.katachi.script.internal.COMMAND_SPECS

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

    "skill の中の相対リンクは、どれも在るファイルを指す" {
        val broken = skill.walkTopDown().filter { it.isFile && it.extension == "md" }
            .flatMap { file ->
                MD_LINK.findAll(file.readText()).map { it.groupValues[1] }
                    .filterNot { URL_SCHEME.containsMatchIn(it) || it.startsWith("#") }
                    .filterNot { file.parentFile.resolve(it.substringBefore("#")).exists() }
                    .map { "${file.relativeTo(skill)} -> $it" }
            }
            .toList()
        broken.shouldBeEmpty()
    }

    "skill が打たせる katachi のコマンドは、どれも CLI に在る（KATACHI-329 で消したものを使わない）" {
        val known = COMMAND_SPECS.map { if (it.agent) "agent ${it.name}" else it.name }.toSet() + "--version"
        val used = skill.walkTopDown().filter { it.isFile && it.extension == "md" }
            .flatMap { file -> commandsIn(file.readText()).map { "${file.relativeTo(skill)}: katachi $it" to it } }
            .toList()
        used.map { it.second }.toSet().shouldContainAll("agent test", "doctor", "scaffold", "lint")
        used.filterNot { (_, command) -> command in known || command in NAMED_AS_HISTORY }.map { it.first }.shouldBeEmpty()
    }
})

/** Old commands the skill names on purpose, to tell an older working directory apart. */
private val NAMED_AS_HISTORY = setOf("agent init")

private val COMMAND = Regex("""^(?:\S+=\S+ )?katachi ((?:agent )?[a-z-]+)""")

/** `katachi <command>` in code spans and in the lines of fenced code blocks; prose says "katachi is …" too. */
private fun commandsIn(markdown: String): List<String> {
    var fenced = false
    return markdown.lines().flatMap { line ->
        if (line.trimStart().startsWith("```")) {
            fenced = !fenced
            return@flatMap emptyList()
        }
        val spans = Regex("`([^`]+)`").findAll(line).map { it.groupValues[1] }
        val candidates = if (fenced) sequenceOf(line.trim()) else spans
        candidates.mapNotNull { COMMAND.find(it)?.groupValues?.get(1) }.toList()
    }
}

/** `[text](target)`; `target` is a URL, an anchor or a path relative to the Markdown file. */
private val MD_LINK = Regex("""\]\(([^)\s]+)\)""")
private val URL_SCHEME = Regex("""^[a-z]+:""")

/** "mark `1-2` done", "mark `4-1`, `4-2` and `4-3` done". */
private val MARK_DONE = Regex("""[Mm]ark (`[0-9]-[0-9A-D]`(, | and )?)+ done""")
private val ITEM_ID = Regex("""[0-9]-[0-9A-D]""")

private fun jsonBlock(file: File, id: String): JsonObject {
    val lines = file.readLines()
    val start = lines.indexOf("""<script type="application/json" id="$id">""")
    val end = lines.subList(start, lines.size).indexOf("</script>") + start
    return Json.parseToJsonElement(lines.subList(start + 1, end).joinToString("\n")).jsonObject
}
