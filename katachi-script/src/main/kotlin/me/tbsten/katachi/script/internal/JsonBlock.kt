package me.tbsten.katachi.script.internal

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

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

internal fun parseJsonOrNull(text: String): JsonElement? =
    runCatching { Json.parseToJsonElement(text) }.getOrNull()
