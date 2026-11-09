package me.tbsten.katachi.script.internal

import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText

/** Which file a short name means, with its JSON block id. */
internal enum class Target(val cliName: String, val file: String, val blockId: String, val questionPrefix: String, val label: Msg) {
    CHECK_LIST("check-list", CHECK_LIST_FILE, "checklist", "q", Messages.Checklist.checkList),
    REPORT("report", REPORT_FILE, "report", "cq", Messages.Checklist.report),
    ;

    companion object {
        fun of(name: String): Target? = when (name) {
            "check-list", "checklist" -> CHECK_LIST
            "report" -> REPORT
            else -> null
        }
    }
}

/** The JSON of [target]'s block as plain values, or stops with [unreadable]. */
internal fun ScriptContext.readTree(html: Path, id: String, unreadable: Msg): JMap {
    val block = extractJsonBlock(html.readText(), id) as? JsonBlock.Found ?: die(unreadable)
    return parseJsonOrNull(block.text)?.toTree().asMap() ?: die(unreadable)
}

/**
 * Reads the check list, lets [edit] change it, and writes it back, all under the lock: the kit's
 * `edit_checklist`. [edit] returns false to leave the file as it was.
 */
internal fun ScriptContext.editChecklist(edit: (JMap) -> Boolean) {
    val workdir = requireWorkdir()
    val checkList = workdir.resolve(CHECK_LIST_FILE)
    if (!checkList.isRegularFile()) die(Messages.Checklist.notFoundRunInit(fileUri(checkList)))
    withLock(checkList) {
        val data = readTree(checkList, "checklist", Messages.Checklist.couldNotExtractJson(fileUri(checkList)))
        if (edit(data)) replaceJsonBlockQuietly(checkList, "checklist", data.toJsonElement())
    }
}

/** Every item of every step, in the check list's order. */
internal fun JMap.allItems(): List<JMap> = listOrEmpty("steps").maps().flatMap { it.listOrEmpty("items").maps() }

/** The items of the steps that are not optional. */
internal fun JMap.requiredItems(): List<JMap> = listOrEmpty("steps").maps().filter { it["optional"] != true }.flatMap { it.listOrEmpty("items").maps() }

internal fun JMap.optionalItems(): List<JMap> = listOrEmpty("steps").maps().filter { it["optional"] == true }.flatMap { it.listOrEmpty("items").maps() }

/** The guide section an item belongs to: 5-1 / 5-2 are 5, 6-x none (the kit's `section_of`). */
internal fun sectionOf(itemId: String): String? = when {
    itemId == "5-1" || itemId == "5-2" -> "5"
    itemId.startsWith("6-") -> null
    else -> itemId
}

/**
 * The section to read next: after the last done item before the first unfinished one, the next
 * section in the guide's table of contents, so that sections without items (0-3, the chapter
 * preambles) are not skipped. The kit's `next_section`.
 */
internal fun nextSection(items: List<JMap>, toc: List<String>): String? {
    val pending = items.filter { !truthy(it["done"]) }.mapNotNull { it.str("id") }.filter { sectionOf(it) != null }
    val target = pending.firstOrNull()?.let(::sectionOf) ?: return null
    if (target !in toc) return target
    val limit = toc.indexOf(target)
    val done = items.filter { truthy(it["done"]) }
        .mapNotNull { it.str("id")?.let(::sectionOf) }
        .filter { it in toc }
        .map { toc.indexOf(it) }
        .filter { it < limit }
    if (done.isEmpty()) return target
    return toc[done.max() + 1]
}

// ---------------------------------------------------------------- question ids

/** An element without an id gets one from its position, as the page's `questionId` does. */
internal fun questionId(target: Target, question: JMap, position: Int): String =
    question.str("id")?.takeIf { it.isNotEmpty() } ?: "${target.questionPrefix}${position + 1}"

/** The number of [id] when it is one of [target]'s (`q3` for the check list, `cq3` for the report). */
internal fun questionNumber(target: Target, id: Any?): Int? =
    Regex("^${target.questionPrefix}([0-9]+)$").matchEntire(id?.let(::pyStr) ?: "")?.groupValues?.get(1)?.toIntOrNull()

internal fun targetOfQuestionId(id: Any?): Target? = Target.entries.firstOrNull { questionNumber(it, id) != null }

internal fun ScriptContext.targetName(target: Target): String = pick(target.label)
