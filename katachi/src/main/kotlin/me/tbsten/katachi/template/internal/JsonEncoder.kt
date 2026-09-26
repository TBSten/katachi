package me.tbsten.katachi.template.internal

import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.encoding.AbstractEncoder
import kotlinx.serialization.encoding.CompositeEncoder
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import me.tbsten.katachi.template.KatachiUnsupportedTemplateJsonValueException

/**
 * [value] as a JSON document: UTF-8 text without a BOM, indented by two spaces, ending in one
 * line break.
 *
 * The writing half of `StringMapDecoder`. `kotlinx-serialization-json` would do this, and was not
 * added: katachi already depends on `kotlinx-serialization-core` alone, and a user's test runtime
 * classpath should not grow for a file only the IDE plugin reads.
 *
 * The rules are part of the contract with the katachi IDE plugin, which parses this output:
 * - every key is written, in declaration order: `null` as `null`, an empty list as `[]`
 * - an enum is written by its serial name
 * - `"` `\` and the control characters are escaped; everything else, `/` and non-ASCII included,
 *   is written as it is
 * - a list of plain values stays on one line (`["a", "b"]`), a list of objects one per line
 *
 * Only what the template descriptions hold is supported: objects, lists, strings, whole numbers,
 * Booleans, enums and `null`. A map, a fraction or a polymorphic value is refused with
 * [KatachiUnsupportedTemplateJsonValueException], since no type written here carries one.
 */
internal fun <T> encodeToJson(serializer: SerializationStrategy<T>, value: T): String {
    var root: JsonNode? = null
    NodeEncoder(container = null) { root = it }.encodeSerializableValue(serializer, value)
    val out = StringBuilder()
    (root ?: JsonNode.Null).writeTo(out, indent = 0)
    return out.append('\n').toString()
}

private sealed interface JsonNode {
    object Null : JsonNode
    class Literal(val text: String) : JsonNode
    class Str(val value: String) : JsonNode
    class Obj(val members: MutableList<Pair<String, JsonNode>> = mutableListOf()) : JsonNode
    class Arr(val items: MutableList<JsonNode> = mutableListOf()) : JsonNode
}

private fun JsonNode.writeTo(out: StringBuilder, indent: Int) {
    when (this) {
        JsonNode.Null -> out.append("null")
        is JsonNode.Literal -> out.append(text)
        is JsonNode.Str -> out.appendJsonString(value)
        is JsonNode.Obj -> {
            if (members.isEmpty()) {
                out.append("{}")
                return
            }
            out.append("{\n")
            members.forEachIndexed { index, (name, node) ->
                out.indent(indent + 1).appendJsonString(name).append(": ")
                node.writeTo(out, indent + 1)
                if (index < members.lastIndex) out.append(',')
                out.append('\n')
            }
            out.indent(indent).append('}')
        }

        is JsonNode.Arr -> when {
            items.isEmpty() -> out.append("[]")
            // Plain values on one line keep a golden file's diff readable: a renamed parameter
            // shows as one changed line rather than one line per name.
            items.none { it is JsonNode.Obj || it is JsonNode.Arr } -> {
                out.append('[')
                items.forEachIndexed { index, node ->
                    if (index > 0) out.append(", ")
                    node.writeTo(out, indent)
                }
                out.append(']')
            }

            else -> {
                out.append("[\n")
                items.forEachIndexed { index, node ->
                    out.indent(indent + 1)
                    node.writeTo(out, indent + 1)
                    if (index < items.lastIndex) out.append(',')
                    out.append('\n')
                }
                out.indent(indent).append(']')
            }
        }
    }
}

private fun StringBuilder.indent(level: Int): StringBuilder {
    repeat(level) { append("  ") }
    return this
}

private fun StringBuilder.appendJsonString(value: String): StringBuilder {
    append('"')
    for (char in value) {
        when (char) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            '\b' -> append("\\b")
            '\u000C' -> append("\\f")
            else ->
                if (char < ' ') {
                    append("\\u").append(char.code.toString(16).padStart(4, '0'))
                } else {
                    append(char)
                }
        }
    }
    return append('"')
}

/**
 * Builds the [JsonNode] tree of one value. The root has no [container] and hands its value to
 * [emit]; every structure below it gets an encoder of its own, whose [emit] puts the finished
 * structure into the parent under the element being written.
 */
private class NodeEncoder(
    private val container: JsonNode?,
    private val emit: (JsonNode) -> Unit,
) : AbstractEncoder() {
    override val serializersModule: SerializersModule = EmptySerializersModule()

    private var elementName: String? = null

    private fun put(node: JsonNode) {
        when (container) {
            is JsonNode.Obj -> container.members += (elementName ?: unsupported("an unnamed member")) to node
            is JsonNode.Arr -> container.items += node
            else -> emit(node)
        }
    }

    override fun encodeElement(descriptor: SerialDescriptor, index: Int): Boolean {
        elementName = descriptor.getElementName(index)
        return true
    }

    override fun beginStructure(descriptor: SerialDescriptor): CompositeEncoder {
        val structure = when (descriptor.kind) {
            StructureKind.CLASS, StructureKind.OBJECT -> JsonNode.Obj()
            StructureKind.LIST -> JsonNode.Arr()
            else -> unsupported(descriptor.kind.toString())
        }
        return NodeEncoder(structure, ::put)
    }

    override fun endStructure(descriptor: SerialDescriptor) {
        emit(container ?: unsupported("the end of a structure that never began"))
    }

    override fun encodeNull(): Unit = put(JsonNode.Null)
    override fun encodeString(value: String): Unit = put(JsonNode.Str(value))
    override fun encodeChar(value: Char): Unit = put(JsonNode.Str(value.toString()))
    override fun encodeBoolean(value: Boolean): Unit = put(JsonNode.Literal(value.toString()))
    override fun encodeInt(value: Int): Unit = put(JsonNode.Literal(value.toString()))
    override fun encodeLong(value: Long): Unit = put(JsonNode.Literal(value.toString()))
    override fun encodeShort(value: Short): Unit = put(JsonNode.Literal(value.toString()))
    override fun encodeByte(value: Byte): Unit = put(JsonNode.Literal(value.toString()))
    override fun encodeFloat(value: Float): Unit = unsupported("Float")
    override fun encodeDouble(value: Double): Unit = unsupported("Double")

    override fun encodeEnum(enumDescriptor: SerialDescriptor, index: Int): Unit =
        put(JsonNode.Str(enumDescriptor.getElementName(index)))

    override fun encodeValue(value: Any): Unit = unsupported(value::class.simpleName ?: "an anonymous value")

    private fun unsupported(kind: String): Nothing = throw KatachiUnsupportedTemplateJsonValueException(kind)
}
