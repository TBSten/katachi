package me.tbsten.katachi.check.internal

import me.tbsten.katachi.check.KatachiInvalidBaselineFileException
import me.tbsten.katachi.check.KatachiInvalidBaselineFileException.Problem
import me.tbsten.katachi.check.KatachiUnsupportedBaselineVersionException

/**
 * The version of the file format this katachi writes, and the newest it reads.
 */
internal const val BASELINE_FORMAT_VERSION: Int = 1

/**
 * [ledger] as the text of a baseline file: one entry per line, checks in name order and entries
 * in key order, `count` left out when it is 1, LF line breaks and one at the end.
 *
 * Written by hand rather than through a serializer so that the one-entry-per-line shape holds:
 * adding or removing an entry is then a one-line diff, and two branches touching different
 * entries do not conflict.
 */
internal fun renderBaseline(ledger: BaselineLedger): String = buildString {
    append("{\n")
    append("  \"version\": ").append(BASELINE_FORMAT_VERSION).append(",\n")
    val byCheck = ledger.entries.entries.sortedBy { it.key }.groupBy { it.key.check }.toSortedMap()
    if (byCheck.isEmpty()) {
        append("  \"checks\": {}\n")
    } else {
        append("  \"checks\": {\n")
        byCheck.entries.forEachIndexed { checkIndex, (check, entries) ->
            append("    ").appendJsonString(check).append(": [\n")
            entries.forEachIndexed { index, (key, count) ->
                append("      ").appendEntry(key, count)
                append(if (index == entries.lastIndex) "\n" else ",\n")
            }
            append("    ]")
            append(if (checkIndex == byCheck.size - 1) "\n" else ",\n")
        }
        append("  }\n")
    }
    append("}\n")
}

private fun StringBuilder.appendEntry(key: BaselineKey, count: Int): StringBuilder {
    val fields = buildList {
        add("rule" to key.rule)
        add("path" to key.path)
        key.role?.let { add("role" to it) }
        key.constraint?.let { add("constraint" to it) }
        key.declaration?.let { add("declaration" to it) }
    }
    append('{')
    fields.forEachIndexed { index, (name, value) ->
        if (index > 0) append(", ")
        appendJsonString(name).append(": ").appendJsonString(value)
    }
    if (count != 1) append(", \"count\": ").append(count)
    return append('}')
}

private fun StringBuilder.appendJsonString(value: String): StringBuilder {
    append('"')
    for (char in value) {
        when {
            char == '"' -> append("\\\"")
            char == '\\' -> append("\\\\")
            char == '\n' -> append("\\n")
            char == '\r' -> append("\\r")
            char == '\t' -> append("\\t")
            char < ' ' -> append("\\u").append(char.code.toString(16).padStart(4, '0'))
            else -> append(char)
        }
    }
    return append('"')
}

/** The fields a baseline file has at the top. */
private val DOCUMENT_FIELDS = setOf("version", "checks")

/** The fields an entry has. */
private val ENTRY_FIELDS = setOf("rule", "path", "role", "constraint", "declaration", "count")

/**
 * Reads the text of a baseline file.
 *
 * Parsed by hand: `:katachi` depends on `kotlinx-serialization-core` alone, and a user's test
 * classpath should not grow a JSON library for one small file. Doing it here also lets every
 * complaint name the line it is about. Strict on purpose -- an unknown field, a name given twice
 * -- because the file is katachi's own output, and anything else in it is a hand edit or a merge
 * that would otherwise be read as something it is not.
 *
 * @param file the file as a `file:///...` URI, for the messages.
 * @throws KatachiInvalidBaselineFileException when the text is not a baseline file.
 * @throws KatachiUnsupportedBaselineVersionException when a newer katachi wrote it.
 */
internal fun parseBaseline(text: String, file: String): BaselineLedger {
    mergeConflictMarkerIn(text)?.let { (line, marker) -> throw invalidBaseline(file, line, Problem.MergeConflict, marker) }
    val root = JsonReader(text, file).readDocument()
    val document = root as? JsonNode.Obj
        ?: throw invalidBaseline(file, root.line, Problem.WrongType, "the file must hold one JSON object")
    document.requireKnownFields(DOCUMENT_FIELDS, file)

    val version = document.field("version") ?: throw invalidBaseline(file, document.line, Problem.MissingField, "version")
    val versionNumber = version.wholeNumber("version", file)
    if (versionNumber > BASELINE_FORMAT_VERSION) {
        throw KatachiUnsupportedBaselineVersionException(file, versionNumber, BASELINE_FORMAT_VERSION)
    }
    if (versionNumber < 1) throw invalidBaseline(file, version.line, Problem.WrongType, "\"version\" must be 1 or more")

    val checks = document.field("checks") ?: throw invalidBaseline(file, document.line, Problem.MissingField, "checks")
    val checksObject = checks as? JsonNode.Obj
        ?: throw invalidBaseline(file, checks.line, Problem.WrongType, "\"checks\" must be an object")

    val entries = LinkedHashMap<BaselineKey, Int>()
    val firstLines = HashMap<BaselineKey, Int>()
    for ((check, value) in checksObject.members) {
        val list = value as? JsonNode.Arr
            ?: throw invalidBaseline(file, value.line, Problem.WrongType, "the entries of \"$check\" must be an array")
        for (item in list.items) {
            val entry = item as? JsonNode.Obj
                ?: throw invalidBaseline(file, item.line, Problem.WrongType, "an entry must be an object")
            entry.requireKnownFields(ENTRY_FIELDS, file)
            val key = BaselineKey(
                check = check,
                rule = entry.requiredString("rule", file),
                path = entry.requiredString("path", file),
                role = entry.optionalString("role", file),
                constraint = entry.optionalString("constraint", file),
                declaration = entry.optionalString("declaration", file),
            )
            val count = entry.field("count")?.wholeNumber("count", file) ?: 1
            if (count < 1) throw invalidBaseline(file, entry.line, Problem.CountBelowOne, "$count")
            val first = firstLines[key]
            if (first != null) throw invalidBaseline(file, entry.line, Problem.DuplicateEntry, "$first")
            firstLines[key] = entry.line
            entries[key] = count
        }
    }
    return BaselineLedger(entries)
}

private fun JsonNode.Obj.field(name: String): JsonNode? = members.firstOrNull { it.first == name }?.second

private fun JsonNode.Obj.requireKnownFields(known: Set<String>, file: String) {
    val unknown = members.firstOrNull { it.first !in known } ?: return
    throw invalidBaseline(file, unknown.second.line, Problem.UnknownField, unknown.first)
}

/** This number as an [Int], telling a fraction apart from a number too large to hold. */
private fun JsonNode.wholeNumber(name: String, file: String): Int {
    val text = (this as? JsonNode.Num)?.text
        ?: throw invalidBaseline(file, line, Problem.WrongType, "\"$name\" must be a whole number")
    if (text.any { it == '.' || it == 'e' || it == 'E' }) {
        throw invalidBaseline(file, line, Problem.WrongType, "\"$name\" must be a whole number")
    }
    return text.toIntOrNull() ?: throw invalidBaseline(
        file,
        line,
        Problem.WrongType,
        "\"$name\" is too ${if (text.startsWith('-')) "small" else "large"} ($text); it must fit in 32 bits",
    )
}

private fun JsonNode.Obj.requiredString(name: String, file: String): String {
    val node = field(name) ?: throw invalidBaseline(file, line, Problem.MissingField, name)
    return (node as? JsonNode.Str)?.value
        ?: throw invalidBaseline(file, node.line, Problem.WrongType, "\"$name\" must be a string")
}

private fun JsonNode.Obj.optionalString(name: String, file: String): String? {
    val node = field(name) ?: return null
    if (node is JsonNode.Null) return null
    return (node as? JsonNode.Str)?.value
        ?: throw invalidBaseline(file, node.line, Problem.WrongType, "\"$name\" must be a string")
}
