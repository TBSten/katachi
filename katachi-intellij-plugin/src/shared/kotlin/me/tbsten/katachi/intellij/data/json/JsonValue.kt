package me.tbsten.katachi.intellij.data.json

internal sealed interface JsonValue {
    data class JsonObject(val members: Map<String, JsonValue>) : JsonValue

    data class JsonArray(val elements: List<JsonValue>) : JsonValue

    data class JsonString(val value: String) : JsonValue

    data class JsonNumber(val text: String) : JsonValue

    data class JsonBoolean(val value: Boolean) : JsonValue

    data object JsonNull : JsonValue
}

internal val JsonValue.typeName: String
    get() = when (this) {
        is JsonValue.JsonObject -> "object"
        is JsonValue.JsonArray -> "array"
        is JsonValue.JsonString -> "string"
        is JsonValue.JsonNumber -> "number"
        is JsonValue.JsonBoolean -> "boolean"
        JsonValue.JsonNull -> "null"
    }
