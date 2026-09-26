package me.tbsten.katachi.intellij.data.json

/**
 * A parsed JSON value. The plugin reads one small, known document, so a tree of these is enough and
 * no JSON library (or its classloading from the IDE) is needed.
 */
internal sealed interface JsonValue {
    data class JsonObject(val members: Map<String, JsonValue>) : JsonValue

    data class JsonArray(val elements: List<JsonValue>) : JsonValue

    data class JsonString(val value: String) : JsonValue

    /** Kept as text: the contract only has integers, and the reader decides how to read them. */
    data class JsonNumber(val text: String) : JsonValue

    data class JsonBoolean(val value: Boolean) : JsonValue

    data object JsonNull : JsonValue
}

/** The JSON type names used in error messages. */
internal val JsonValue.typeName: String
    get() = when (this) {
        is JsonValue.JsonObject -> "object"
        is JsonValue.JsonArray -> "array"
        is JsonValue.JsonString -> "string"
        is JsonValue.JsonNumber -> "number"
        is JsonValue.JsonBoolean -> "boolean"
        JsonValue.JsonNull -> "null"
    }
