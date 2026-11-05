package me.tbsten.katachi.script.internal

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal sealed interface JsonBlock {
    /** The lines between the tags, without the tags. */
    data class Found(val lines: List<String>) : JsonBlock {
        val text: String get() = lines.joinToString("") { it + "\n" }
    }

    data object NotFound : JsonBlock

    /** The opening tag was there but no `</script>` alone on a line followed it. */
    data object NotClosed : JsonBlock
}

private fun openTag(id: String) = "<script type=\"application/json\" id=\"$id\">"

private const val CLOSE_TAG = "</script>"

/**
 * The `<script type="application/json" id="...">` block the check list and the report keep their
 * data in. Both tags have to stand alone on their lines, as the templates write them; the kit's
 * awk reads them the same way, so the two never disagree about where a block ends.
 */
internal fun extractJsonBlock(html: String, id: String): JsonBlock {
    val lines = html.lines()
    val start = lines.indexOf(openTag(id))
    if (start < 0) return JsonBlock.NotFound
    val end = lines.subList(start + 1, lines.size).indexOf(CLOSE_TAG)
    if (end < 0) return JsonBlock.NotClosed
    return JsonBlock.Found(lines.subList(start + 1, start + 1 + end))
}

/** [html] with the block's lines replaced by [body]; null when the block is not there. */
internal fun replaceJsonBlock(html: String, id: String, body: String): String? {
    val lines = html.lines()
    val start = lines.indexOf(openTag(id))
    if (start < 0) return null
    val end = lines.subList(start + 1, lines.size).indexOf(CLOSE_TAG)
    if (end < 0) return null
    val bodyLines = body.removeSuffix("\n").lines()
    val result = lines.subList(0, start + 1) + bodyLines + lines.subList(start + 1 + end, lines.size)
    return result.joinToString("\n")
}

/** `</script>` inside a JSON string would end the block for the browser; `<\/script>` is the same string. */
internal fun escapeScriptEnd(json: String): String = json.replace("</script", "<\\/script")

internal val prettyJson: Json = Json {
    prettyPrint = true
    prettyPrintIndent = "  "
}

internal fun parseJsonOrNull(text: String): JsonElement? =
    runCatching { Json.parseToJsonElement(text) }.getOrNull()

/** [patch] over [base], objects merged key by key, everything else (arrays too) replaced whole. */
internal fun deepMerge(base: JsonElement, patch: JsonElement): JsonElement {
    if (base !is JsonObject || patch !is JsonObject) return patch
    val merged = LinkedHashMap<String, JsonElement>(base)
    for ((key, value) in patch) {
        val existing = merged[key]
        merged[key] = if (existing != null && value is JsonObject && existing is JsonObject) deepMerge(existing, value) else value
    }
    return JsonObject(merged)
}

/** The node at a dot-separated [key] (`meta.moduleDir`, `questions.0`), or null when the path leaves the tree. */
internal fun lookupJsonPath(root: JsonElement, key: String): JsonElement? {
    var node: JsonElement = root
    for (part in key.split(".")) {
        node = when (node) {
            is JsonObject -> node[part] ?: return null
            is JsonArray -> part.toIntOrNull()?.takeIf { it in node.indices }?.let { node[it] } ?: return null
            else -> return null
        }
    }
    return node
}

/** A string bare, anything else as JSON: what `data get <key>` prints. */
internal fun renderJsonValue(node: JsonElement): String =
    if (node is JsonPrimitive && node.isString) node.content else prettyJson.encodeToString(JsonElement.serializer(), node)
