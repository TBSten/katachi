package me.tbsten.katachi.test.script

import java.io.File

/**
 * The rules of `docs/prh.yml`, read the way textlint-rule-prh reads them, so that the CLI's messages
 * are checked against the same dictionary as the docs.
 */
internal class PrhDictionary(val rules: List<PrhRule>) {
    /** Every place in [text] a rule would rewrite. */
    fun violations(text: String): List<PrhViolation> = rules.flatMap { rule ->
        rule.patterns.flatMap { pattern ->
            pattern.findAll(text).mapNotNull { match ->
                val replaced = match.value.replace(pattern, rule.replacement)
                if (replaced == match.value) null else PrhViolation(rule, match.value, replaced)
            }
        }
    }

    companion object {
        fun load(file: File): PrhDictionary {
            val root = MiniYaml.parse(file.readText()) as Map<*, *>
            val rules = (root["rules"] as List<*>).map { raw ->
                val rule = raw as Map<*, *>
                val expected = rule["expected"] as String
                val patterns = when (val pattern = rule["pattern"] ?: expected) {
                    is String -> listOf(pattern)
                    is List<*> -> pattern.map { it as String }
                    else -> error("prh.yml: pattern of '$expected' is neither a string nor a list: $pattern")
                }
                val specs = (rule["specs"] as List<*>?).orEmpty().map {
                    val spec = it as Map<*, *>
                    PrhSpec(spec["from"] as String, spec["to"] as String)
                }
                PrhRule(expected, patterns.map(::toRegex), rule["prh"] as String?, specs)
            }
            return PrhDictionary(rules)
        }

        // prh treats "/…/" as a JavaScript regex and anything else as a literal.
        private fun toRegex(pattern: String): Regex =
            if (pattern.length > 1 && pattern.startsWith("/") && pattern.endsWith("/")) {
                Regex(pattern.substring(1, pattern.length - 1))
            } else {
                Regex(Regex.escape(pattern))
            }
    }
}

internal class PrhRule(val expected: String, val patterns: List<Regex>, val prh: String?, val specs: List<PrhSpec>) {
    // JavaScript's "$1" in `expected` means the same in Kotlin's replace; a backslash does not.
    val replacement: String = expected.replace("\\", "\\\\")

    fun apply(text: String): String = patterns.fold(text) { acc, pattern -> acc.replace(pattern, replacement) }
}

internal class PrhSpec(val from: String, val to: String)

internal class PrhViolation(val rule: PrhRule, val found: String, val replaced: String)

/**
 * The part of YAML that prh.yml uses: block maps, block lists, plain and quoted scalars, comments.
 * Anything else is an error rather than a silent misread.
 */
internal object MiniYaml {
    private class Line(val indent: Int, val text: String, val number: Int)

    fun parse(source: String): Any? {
        val lines = source.lines().mapIndexedNotNull { index, raw ->
            val text = raw.trimEnd()
            val content = text.trimStart()
            if (content.isEmpty() || content.startsWith("#")) null else Line(text.length - content.length, content, index + 1)
        }
        val (value, next) = parseBlock(lines, 0, lines.firstOrNull()?.indent ?: 0)
        check(next == lines.size) { "prh.yml line ${lines[next].number}: unexpected indentation" }
        return value
    }

    private fun parseBlock(lines: List<Line>, start: Int, indent: Int): Pair<Any?, Int> =
        if (lines[start].text.startsWith("- ") || lines[start].text == "-") parseList(lines, start, indent) else parseMap(lines, start, indent)

    private fun parseList(lines: List<Line>, start: Int, indent: Int): Pair<List<Any?>, Int> {
        val items = mutableListOf<Any?>()
        var i = start
        while (i < lines.size && lines[i].indent == indent && lines[i].text.startsWith("-")) {
            val rest = lines[i].text.removePrefix("-").trimStart()
            val itemIndent = indent + (lines[i].text.length - rest.length)
            if (rest.isEmpty()) {
                val (value, next) = parseBlock(lines, i + 1, lines[i + 1].indent)
                items += value
                i = next
            } else if (keyOf(rest) != null) {
                // "- key: value" starts a map whose later keys sit under the first key.
                val inline = lines.toMutableList().also { it[i] = Line(itemIndent, rest, lines[i].number) }
                val (value, next) = parseMap(inline, i, itemIndent)
                items += value
                i = next
            } else {
                items += scalar(rest, lines[i].number)
                i++
            }
        }
        return items to i
    }

    private fun parseMap(lines: List<Line>, start: Int, indent: Int): Pair<Map<String, Any?>, Int> {
        val map = linkedMapOf<String, Any?>()
        var i = start
        while (i < lines.size && lines[i].indent == indent && !lines[i].text.startsWith("- ")) {
            val line = lines[i]
            val key = keyOf(line.text) ?: error("prh.yml line ${line.number}: expected 'key: value'")
            val rest = line.text.substring(key.length + 1).trim()
            if (rest.isEmpty()) {
                val childIndent = lines.getOrNull(i + 1)?.indent ?: -1
                // A list may sit at the same indentation as its key.
                if (childIndent > indent || (childIndent == indent && lines[i + 1].text.startsWith("- "))) {
                    val (value, next) = parseBlock(lines, i + 1, childIndent)
                    map[key] = value
                    i = next
                } else {
                    map[key] = null
                    i++
                }
            } else {
                map[key] = scalar(rest, line.number)
                i++
            }
        }
        return map to i
    }

    private fun keyOf(text: String): String? = Regex("^([A-Za-z_][A-Za-z0-9_]*):(?:\\s|$)").find(text)?.groupValues?.get(1)

    private fun scalar(text: String, number: Int): String = when {
        text.startsWith("'") -> {
            check(text.endsWith("'") && text.length >= 2) { "prh.yml line $number: unterminated single-quoted string" }
            text.substring(1, text.length - 1).replace("''", "'")
        }
        text.startsWith("\"") -> {
            check(text.endsWith("\"") && text.length >= 2) { "prh.yml line $number: unterminated double-quoted string" }
            text.substring(1, text.length - 1).replace("\\\"", "\"").replace("\\\\", "\\")
        }
        else -> {
            check(!text.startsWith("[") && !text.startsWith("{") && !text.startsWith("|") && !text.startsWith(">")) {
                "prh.yml line $number: flow and block scalars are not supported"
            }
            text.substringBefore(" #")
        }
    }
}
