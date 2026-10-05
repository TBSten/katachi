package me.tbsten.katachi.intellij.data.json

import java.nio.file.Path
import java.time.Instant
import java.time.format.DateTimeParseException

/**
 * A value at [location] in `definition.json`, read with the contract's types. A key that is missing or has
 * another type is a [KatachiIncompatibleDefinitionJsonException] naming where.
 */
internal class DefinitionJsonReader(
    private val value: JsonValue,
    private val location: String,
    private val file: Path,
) {
    private fun child(key: String): DefinitionJsonReader? {
        val obj = value as? JsonValue.JsonObject ?: throw mismatch(location, "object", value)
        val found = obj.members[key] ?: return null
        return DefinitionJsonReader(found, if (location == "$") key else "$location.$key", file)
    }

    private fun member(key: String): DefinitionJsonReader =
        child(key) ?: throw KatachiIncompatibleDefinitionJsonException(file, pathOf(key), "a value", actual = null)

    private fun pathOf(key: String) = if (location == "$") key else "$location.$key"

    val raw: JsonValue get() = value

    fun hasKey(key: String): Boolean = (value as? JsonValue.JsonObject)?.members?.containsKey(key) == true

    fun obj(key: String): DefinitionJsonReader = member(key).also {
        if (it.value !is JsonValue.JsonObject) throw mismatch(it.location, "object", it.value)
    }

    /** The object at [key], or `null` when it is `null`. A missing key is incompatible. */
    fun nullableObj(key: String): DefinitionJsonReader? {
        val found = member(key)
        return when (found.value) {
            JsonValue.JsonNull -> null
            is JsonValue.JsonObject -> found
            else -> throw mismatch(found.location, "object or null", found.value)
        }
    }

    fun string(key: String): String = member(key).stringValue("string")

    fun nullableString(key: String): String? {
        val found = member(key)
        return if (found.value == JsonValue.JsonNull) null else found.stringValue("string or null")
    }

    private fun stringValue(expected: String): String =
        (value as? JsonValue.JsonString)?.value ?: throw mismatch(location, expected, value)

    fun boolean(key: String): Boolean = member(key).booleanValue("boolean")

    /** The boolean at [key], or [default] when the key is absent: a flag a katachi from before it does not write. */
    fun optionalBoolean(key: String, default: Boolean): Boolean = if (hasKey(key)) boolean(key) else default

    fun nullableBoolean(key: String): Boolean? {
        val found = member(key)
        return if (found.value == JsonValue.JsonNull) null else found.booleanValue("boolean or null")
    }

    private fun booleanValue(expected: String): Boolean =
        (value as? JsonValue.JsonBoolean)?.value ?: throw mismatch(location, expected, value)

    fun int(key: String): Int = member(key).intValue("integer")

    fun nullableInt(key: String): Int? {
        val found = member(key)
        return if (found.value == JsonValue.JsonNull) null else found.intValue("integer or null")
    }

    private fun intValue(expected: String): Int =
        (value as? JsonValue.JsonNumber)?.text?.toIntOrNull() ?: throw mismatch(location, expected, value)

    fun array(key: String): List<DefinitionJsonReader> = member(key).elements()

    fun nullableArray(key: String): List<DefinitionJsonReader>? {
        val found = member(key)
        return if (found.value == JsonValue.JsonNull) null else found.elements("array or null")
    }

    /** The array at [key], `null` when the key is absent or `null`: a key a katachi from before it does not write. */
    fun optionalNullableArray(key: String): List<DefinitionJsonReader>? =
        if (hasKey(key)) nullableArray(key) else null

    fun strings(key: String): List<String> = array(key).map { it.stringValue("string") }

    fun ints(key: String): List<Int> = array(key).map { it.intValue("integer") }

    private fun elements(expected: String = "array"): List<DefinitionJsonReader> {
        val array = value as? JsonValue.JsonArray ?: throw mismatch(location, expected, value)
        return array.elements.mapIndexed { index, element -> DefinitionJsonReader(element, "$location[$index]", file) }
    }

    /**
     * The ISO-8601 instant at [key] such as `2026-10-04T00:30:12Z`. A string that is not one is as incompatible as a value that
     * is not a string: katachi always writes a time, and the features compare it with a file's modification time.
     */
    fun instant(key: String): Instant {
        val found = member(key)
        val text = found.stringValue("string (ISO-8601 instant)")
        return try {
            Instant.parse(text)
        } catch (failure: DateTimeParseException) {
            throw KatachiIncompatibleDefinitionJsonException(file, found.location, "ISO-8601 instant such as 2026-10-04T00:30:12Z", "string")
        }
    }

    private fun mismatch(at: String, expected: String, actual: JsonValue) =
        KatachiIncompatibleDefinitionJsonException(file, at, expected, actual.typeName)
}
