package me.tbsten.katachi.intellij.data.baseline

import me.tbsten.katachi.intellij.data.json.JsonValue
import me.tbsten.katachi.intellij.data.json.KatachiTemplateJsonException
import me.tbsten.katachi.intellij.data.json.parseJsonText
import java.nio.file.Path

/**
 * Reads the text of a `katachi-baseline.json`: `{"version": 1, "checks": {"<check>": [{"rule": ..., "path": ...}, ...]}}`.
 *
 * Judged by the file's own `"version"`: a version this plugin does not know is [BaselineReading.UnknownVersion] whatever
 * else the file holds, and a broken one is [BaselineReading.Unreadable]. A field the plugin does not need (`role`,
 * `constraint`, `declaration`, `count`, anything newer) is left alone; katachi is the strict one, the plugin only reads.
 *
 * ```kotlin
 * parseBaselineText("""{"version": 2, "checks": {}}""")   // UnknownVersion(2)
 * ```
 */
internal fun parseBaselineText(text: String): BaselineReading {
    val document = try {
        parseJsonText(text, Path.of("katachi-baseline.json"))
    } catch (failure: KatachiTemplateJsonException) {
        return BaselineReading.Unreadable(failure.message.orEmpty().lineSequence().firstOrNull().orEmpty())
    }
    val root = document as? JsonValue.JsonObject ?: return BaselineReading.Unreadable("the file is not a JSON object")
    val version = root.members["version"]
    val number = (version as? JsonValue.JsonNumber)?.text?.toIntOrNull()
    if (number != KNOWN_BASELINE_VERSION) {
        return if (version is JsonValue.JsonNumber) BaselineReading.UnknownVersion(number) else BaselineReading.Unreadable("\"version\" is missing or not a number")
    }
    val checks = root.members["checks"] as? JsonValue.JsonObject ?: return BaselineReading.Unreadable("\"checks\" is missing or not an object")
    val byRule = LinkedHashMap<String, MutableSet<String>>()
    for ((check, entries) in checks.members) {
        val list = entries as? JsonValue.JsonArray ?: return BaselineReading.Unreadable("the entries of \"$check\" are not an array")
        for (entry in list.elements) {
            val fields = entry as? JsonValue.JsonObject ?: return BaselineReading.Unreadable("an entry of \"$check\" is not an object")
            val rule = (fields.members["rule"] as? JsonValue.JsonString)?.value
            val path = (fields.members["path"] as? JsonValue.JsonString)?.value
            if (rule == null || path == null) return BaselineReading.Unreadable("an entry of \"$check\" has no \"rule\" or \"path\" string")
            byRule.getOrPut(rule) { linkedSetOf() } += path
        }
    }
    return BaselineReading.Read(BaselineLedger(byRule))
}
