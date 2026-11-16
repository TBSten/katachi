package me.tbsten.katachi.diff.internal

import java.math.BigDecimal

// Own tree, not JsonEncoder (later layer): the JSON and the YAML are written from the same values in the same order.
internal sealed interface DiffData {
    class Text(val value: String) : DiffData
    class Integer(val value: Int) : DiffData
    class Decimal(val value: Double) : DiffData
    class Flag(val value: Boolean) : DiffData
    data object Null : DiffData
    class Record(val members: List<Pair<String, DiffData>>) : DiffData
    class Items(val items: List<DiffData>) : DiffData
}

internal class DiffRecordBuilder {
    private val members = mutableListOf<Pair<String, DiffData>>()

    // Null is written rather than left out, so every record of a kind has the same keys.
    fun put(name: String, value: String?) {
        members += name to (value?.let(DiffData::Text) ?: DiffData.Null)
    }

    fun put(name: String, value: Int) {
        members += name to DiffData.Integer(value)
    }

    fun put(name: String, value: Double) {
        members += name to DiffData.Decimal(value)
    }

    fun put(name: String, value: Boolean) {
        members += name to DiffData.Flag(value)
    }

    fun put(name: String, value: DiffData?) {
        members += name to (value ?: DiffData.Null)
    }

    fun put(name: String, items: List<DiffData>) {
        members += name to DiffData.Items(items)
    }

    fun build(): DiffData.Record = DiffData.Record(members.toList())
}

internal inline fun diffRecord(block: DiffRecordBuilder.() -> Unit): DiffData.Record =
    DiffRecordBuilder().apply(block).build()

internal fun DiffData.renderJson(): String {
    val out = StringBuilder()
    writeJson(out, indent = 0)
    return out.append('\n').toString()
}

private fun DiffData.writeJson(out: StringBuilder, indent: Int) {
    when (this) {
        is DiffData.Text -> out.appendQuoted(value)
        is DiffData.Integer -> out.append(value)
        is DiffData.Decimal -> out.append(plainDecimal(value))
        is DiffData.Flag -> out.append(value)
        DiffData.Null -> out.append("null")
        is DiffData.Record -> {
            if (members.isEmpty()) {
                out.append("{}")
                return
            }
            out.append("{\n")
            members.forEachIndexed { index, (name, node) ->
                out.indent(indent + 1).appendQuoted(name).append(": ")
                node.writeJson(out, indent + 1)
                if (index < members.lastIndex) out.append(',')
                out.append('\n')
            }
            out.indent(indent).append('}')
        }

        is DiffData.Items -> {
            if (items.isEmpty()) {
                out.append("[]")
                return
            }
            out.append("[\n")
            items.forEachIndexed { index, node ->
                out.indent(indent + 1)
                node.writeJson(out, indent + 1)
                if (index < items.lastIndex) out.append(',')
                out.append('\n')
            }
            out.indent(indent).append(']')
        }
    }
}

internal fun DiffData.renderYaml(): String {
    val out = StringBuilder()
    when (this) {
        is DiffData.Record -> if (members.isEmpty()) out.append("{}\n") else writeYamlMembers(out, indent = 0)
        is DiffData.Items -> if (items.isEmpty()) out.append("[]\n") else writeYamlItems(out, indent = 0)
        else -> out.append(yamlScalar()).append('\n')
    }
    return out.toString()
}

private fun DiffData.Record.writeYamlMembers(out: StringBuilder, indent: Int, firstInline: Boolean = false) {
    members.forEachIndexed { index, (name, node) ->
        // The first key of a record that is a list item sits on the item's "- " line.
        if (index > 0 || !firstInline) out.indent(indent)
        out.append(yamlKey(name)).append(':')
        node.writeYamlValue(out, indent)
    }
}

private fun DiffData.Items.writeYamlItems(out: StringBuilder, indent: Int) {
    for (node in items) {
        out.indent(indent).append("- ")
        when {
            node is DiffData.Record && node.members.isNotEmpty() -> node.writeYamlMembers(out, indent + 1, firstInline = true)
            // A list straight inside a list: flow style keeps it on the item's line.
            node is DiffData.Items && node.items.isNotEmpty() -> out.append(node.renderJsonInline()).append('\n')
            else -> out.append(node.yamlScalar()).append('\n')
        }
    }
}

private fun DiffData.writeYamlValue(out: StringBuilder, indent: Int) {
    when {
        this is DiffData.Record && members.isNotEmpty() -> {
            out.append('\n')
            writeYamlMembers(out, indent + 1)
        }

        this is DiffData.Items && items.isNotEmpty() -> {
            out.append('\n')
            writeYamlItems(out, indent + 1)
        }

        else -> out.append(' ').append(yamlScalar()).append('\n')
    }
}

private fun DiffData.yamlScalar(): String = when (this) {
    // Always quoted, in JSON's own string form: a value then reads as text whatever it holds ("no", "1e3", a
    // commit SHA of digits alone), and the same key is written the same way from one report to the next.
    is DiffData.Text -> quoted(value)
    is DiffData.Integer -> value.toString()
    is DiffData.Decimal -> plainDecimal(value)
    is DiffData.Flag -> value.toString()
    DiffData.Null -> "null"
    is DiffData.Record -> "{}"
    is DiffData.Items -> "[]"
}

// JSON is a subset of YAML's flow style, so a nested list needs no second writer.
private fun DiffData.renderJsonInline(): String = when (this) {
    is DiffData.Items -> items.joinToString(", ", "[", "]") { it.renderJsonInline() }
    is DiffData.Record -> members.joinToString(", ", "{", "}") { (name, node) -> quoted(name) + ": " + node.renderJsonInline() }
    else -> yamlScalar()
}

// Plain only when no YAML reader, of 1.1 or of 1.2, could take the key for anything but this text: a word
// of letters and digits that starts with a letter and is not one YAML reads as a boolean or a null.
private fun yamlKey(name: String): String =
    if (YAML_PLAIN.matches(name) && name.lowercase() !in YAML_RESERVED) name else quoted(name)

private val YAML_PLAIN: Regex = Regex("[A-Za-z][A-Za-z0-9_]*")

private val YAML_RESERVED: Set<String> = setOf("true", "false", "yes", "no", "on", "off", "y", "n", "null")

// Without an exponent: YAML 1.1 reads "1.0E7" as text, since it wants a sign after the E.
private fun plainDecimal(value: Double): String {
    val plain = BigDecimal.valueOf(value).toPlainString()
    return if ('.' in plain) plain else "$plain.0"
}

private const val YAML_UNPRINTABLE: String = "\u2028\u2029\uFEFF\uFFFE\uFFFF"

private fun quoted(value: String): String = StringBuilder().appendQuoted(value).toString()

private fun StringBuilder.indent(level: Int): StringBuilder {
    repeat(level) { append("  ") }
    return this
}

// Escapes past what JSON asks for: YAML's double quoted style takes NEL, LS and PS for line breaks and
// has no place for DEL, the C1 controls, the byte order mark or the two noncharacters, and each is still a
// valid JSON escape.
private fun StringBuilder.appendQuoted(value: String): StringBuilder {
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
                if (char < ' ' || char in '\u007F'..'\u009F' || char in YAML_UNPRINTABLE) {
                    append("\\u").append(char.code.toString(16).padStart(4, '0'))
                } else {
                    append(char)
                }
        }
    }
    return append('"')
}
