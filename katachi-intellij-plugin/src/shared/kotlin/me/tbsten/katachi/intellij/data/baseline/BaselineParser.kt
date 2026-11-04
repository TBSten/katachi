package me.tbsten.katachi.intellij.data.baseline

import me.tbsten.katachi.intellij.data.json.JsonValue
import me.tbsten.katachi.intellij.data.json.KatachiTemplateJsonException
import me.tbsten.katachi.intellij.data.json.parseJsonText
import java.nio.file.Path

// Must accept exactly what katachi's `BaselineJson` reader accepts: a file it would refuse is unreadable here too.
internal fun parseBaselineText(text: String): BaselineReading {
    val document = try {
        parseJsonText(text, Path.of("katachi-baseline.json"), rejectDuplicateNames = true)
    } catch (failure: KatachiTemplateJsonException) {
        return BaselineReading.Unreadable(failure.message.orEmpty().lineSequence().firstOrNull().orEmpty())
    }
    val root = document as? JsonValue.JsonObject ?: return BaselineReading.Unreadable("the file is not a JSON object")
    val version = root.members["version"]
    val number = (version as? JsonValue.JsonNumber)?.text?.toIntOrNull()
    if (number != null && number < 1) return BaselineReading.Unreadable("\"version\" must be 1 or more")
    if (number != KNOWN_BASELINE_VERSION) {
        return if (version is JsonValue.JsonNumber) BaselineReading.UnknownVersion(number) else BaselineReading.Unreadable("\"version\" is missing or not a number")
    }
    root.members.keys.firstOrNull { it !in DOCUMENT_FIELDS }?.let { return BaselineReading.Unreadable("unknown field \"$it\"") }
    val checks = root.members["checks"] as? JsonValue.JsonObject ?: return BaselineReading.Unreadable("\"checks\" is missing or not an object")
    val byRule = LinkedHashMap<String, MutableSet<String>>()
    val seen = HashSet<List<String?>>()
    for ((check, entries) in checks.members) {
        val list = entries as? JsonValue.JsonArray ?: return BaselineReading.Unreadable("the entries of \"$check\" are not an array")
        for (entry in list.elements) {
            val fields = entry as? JsonValue.JsonObject ?: return BaselineReading.Unreadable("an entry of \"$check\" is not an object")
            fields.members.keys.firstOrNull { it !in ENTRY_FIELDS }?.let { return BaselineReading.Unreadable("unknown field \"$it\" in an entry of \"$check\"") }
            val rule = (fields.members["rule"] as? JsonValue.JsonString)?.value
            val path = (fields.members["path"] as? JsonValue.JsonString)?.value
            if (rule == null || path == null) return BaselineReading.Unreadable("an entry of \"$check\" has no \"rule\" or \"path\" string")
            val optional = OPTIONAL_STRING_FIELDS.map { name ->
                when (val value = fields.members[name]) {
                    null, JsonValue.JsonNull -> null
                    is JsonValue.JsonString -> value.value
                    else -> return BaselineReading.Unreadable("\"$name\" of an entry of \"$check\" is not a string")
                }
            }
            val count = fields.members["count"]?.let { value ->
                val whole = (value as? JsonValue.JsonNumber)?.text?.takeIf { text -> text.none { it == '.' || it == 'e' || it == 'E' } }?.toIntOrNull()
                    ?: return BaselineReading.Unreadable("\"count\" of an entry of \"$check\" is not a whole number")
                whole
            } ?: 1
            if (count < 1) return BaselineReading.Unreadable("\"count\" of an entry of \"$check\" is $count, below 1")
            if (!seen.add(listOf(check, rule, path) + optional)) return BaselineReading.Unreadable("\"$check\" files $rule at \"$path\" twice")
            if (check == BaselineLedger.LAYOUT_CHECK) byRule.getOrPut(rule) { linkedSetOf() } += path
        }
    }
    return BaselineReading.Read(BaselineLedger(byRule))
}

private val DOCUMENT_FIELDS = setOf("version", "checks")

private val ENTRY_FIELDS = setOf("rule", "path", "role", "constraint", "declaration", "count")

private val OPTIONAL_STRING_FIELDS = listOf("role", "constraint", "declaration")
