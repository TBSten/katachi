package me.tbsten.katachi.script.internal

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

// The check list and the report are edited the way the kit's embedded python edited them: the JSON
// read into plain maps and lists, changed in place, and written back. These are those plain values.

internal typealias JMap = LinkedHashMap<String, Any?>

internal typealias JList = ArrayList<Any?>

/** Objects become [JMap], arrays [JList], strings and booleans themselves; numbers stay [JsonPrimitive] so they are written back as they were. */
internal fun JsonElement.toTree(): Any? = when (this) {
    is JsonObject -> mapValuesTo(JMap()) { (_, value) -> value.toTree() }
    is JsonArray -> mapTo(JList()) { it.toTree() }
    JsonNull -> null
    is JsonPrimitive -> when {
        isString -> content
        booleanOrNull != null -> booleanOrNull
        else -> this
    }
}

internal fun Any?.toJsonElement(): JsonElement = when (this) {
    null -> JsonNull
    is JsonElement -> this
    is String -> JsonPrimitive(this)
    is Boolean -> JsonPrimitive(this)
    is Number -> JsonPrimitive(this)
    is Map<*, *> -> JsonObject(entries.associate { (key, value) -> key.toString() to value.toJsonElement() })
    is List<*> -> JsonArray(map { it.toJsonElement() })
    else -> JsonPrimitive(toString())
}

/** Python's truthiness, which the kit's checks (`if not i.get("done")`) relied on. */
internal fun truthy(value: Any?): Boolean = when (value) {
    null -> false
    is Boolean -> value
    is String -> value.isNotEmpty()
    is Collection<*> -> value.isNotEmpty()
    is Map<*, *> -> value.isNotEmpty()
    is JsonPrimitive -> value.content.toDoubleOrNull()?.let { it != 0.0 } ?: true
    else -> true
}

/** `str(value)` for what Python would have printed with `%s`. */
internal fun pyStr(value: Any?): String = when (value) {
    null -> "None"
    is Boolean -> if (value) "True" else "False"
    is JsonPrimitive -> value.content
    else -> value.toString()
}

@Suppress("UNCHECKED_CAST")
internal fun Any?.asMap(): JMap? = this as? JMap

@Suppress("UNCHECKED_CAST")
internal fun Any?.asList(): JList? = this as? JList

/** `d.get(key) or {}` for a map. */
internal fun JMap.mapOrEmpty(key: String): JMap = get(key).asMap()?.takeIf { it.isNotEmpty() } ?: JMap()

/** `d.get(key) or []` for a list. */
internal fun JMap.listOrEmpty(key: String): JList = get(key).asList()?.takeIf { it.isNotEmpty() } ?: JList()

/** `d.get(key)` as a string when it is one. */
internal fun JMap.str(key: String): String? = get(key) as? String

/** The maps in a list, skipping anything else (the kit's python would have crashed on those). */
internal fun JList.maps(): List<JMap> = mapNotNull { it.asMap() }
