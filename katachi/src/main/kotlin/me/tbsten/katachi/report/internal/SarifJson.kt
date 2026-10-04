package me.tbsten.katachi.report.internal

/**
 * The JSON a SARIF document is made of: objects, arrays, strings, whole numbers and Booleans.
 *
 * A tree of its own rather than `template.internal.JsonEncoder`: that one sits in a later layer
 * and is driven by a serializer, while a SARIF document is assembled by hand with every key in a
 * fixed order so that two runs over the same violations write the same bytes.
 */
internal sealed interface SarifJson {
    class Str(val value: String) : SarifJson
    class Num(val value: Int) : SarifJson
    class Bool(val value: Boolean) : SarifJson
    class Obj(val members: List<Pair<String, SarifJson>>) : SarifJson
    class Arr(val items: List<SarifJson>) : SarifJson
}

/** Collects the members of one object, in the order they are put, skipping `null` values. */
internal class SarifObjectBuilder {
    private val members = mutableListOf<Pair<String, SarifJson>>()

    fun put(name: String, value: String?) {
        if (value != null) members += name to SarifJson.Str(value)
    }

    fun put(name: String, value: Int?) {
        if (value != null) members += name to SarifJson.Num(value)
    }

    fun put(name: String, value: Boolean) {
        members += name to SarifJson.Bool(value)
    }

    fun put(name: String, value: SarifJson?) {
        if (value != null) members += name to value
    }

    /** Puts [items] as an array, or nothing at all when it is empty and [omitWhenEmpty] holds. */
    fun put(name: String, items: List<SarifJson>, omitWhenEmpty: Boolean = false) {
        if (items.isEmpty() && omitWhenEmpty) return
        members += name to SarifJson.Arr(items)
    }

    fun build(): SarifJson.Obj = SarifJson.Obj(members.toList())
}

internal inline fun sarifObject(block: SarifObjectBuilder.() -> Unit): SarifJson.Obj =
    SarifObjectBuilder().apply(block).build()

/**
 * The document as text: two-space indent, LF line breaks, one line break at the end, `{}` and
 * `[]` on one line only when empty, `"` `\` and the control characters escaped and everything
 * else -- `/` and non-ASCII included -- written as it is.
 */
internal fun SarifJson.render(): String {
    val out = StringBuilder()
    writeTo(out, indent = 0)
    return out.append('\n').toString()
}

private fun SarifJson.writeTo(out: StringBuilder, indent: Int) {
    when (this) {
        is SarifJson.Str -> out.appendJsonString(value)
        is SarifJson.Num -> out.append(value)
        is SarifJson.Bool -> out.append(value)
        is SarifJson.Obj -> {
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

        is SarifJson.Arr -> {
            if (items.isEmpty()) {
                out.append("[]")
                return
            }
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
