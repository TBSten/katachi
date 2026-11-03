package me.tbsten.katachi.konsist.declarations.internal

// Not `KoParentProvider.parents()`: it ends with `distinctBy { it.sourceDeclaration }`, whose lookup searches the whole project scope Konsist infers
// and ran the test JVM out of memory. Same reading as `architecture-test`'s `directSupertypeNamesOf`, which lives in another module and cannot be shared.

internal fun supertypesOf(text: String, name: String): List<SupertypeFact> {
    val afterName = codeAfterNameOf(text, name) ?: return emptyList()
    val list = supertypeListOf(afterName) ?: return emptyList()
    return splitAtTopLevelCommas(list)
        .map { entry -> withoutConstructorCall(withoutDelegation(entry.trim())) }
        .filter { it.isNotEmpty() }
        .map { written ->
            val single = collapseSpaces(written)
            SupertypeFact(
                written = single,
                withoutArguments = canonicalType(withoutTypeArguments(single)),
                canonical = canonicalType(single),
            )
        }
}

internal fun headerTextOf(text: String, annotationTexts: List<String>): String {
    var header = withoutLeadingComments(text)
    for (annotation in annotationTexts) {
        val index = header.indexOf(annotation)
        if (index >= 0) header = header.removeRange(index, index + annotation.length)
    }
    return header
}

internal fun withoutLeadingComments(text: String): String {
    var rest = text.trimStart()
    while (true) {
        rest = when {
            rest.startsWith("/*") -> rest.substringAfter("*/", missingDelimiterValue = "").trimStart()
            rest.startsWith("//") -> rest.substringAfter('\n', missingDelimiterValue = "").trimStart()
            else -> return rest
        }
    }
}

private fun headerRegexOf(name: String): Regex =
    if (name == UNNAMED_COMPANION) {
        Regex("""\bcompanion\s+object\b(?:\s+`?${Regex.escape(name)}`?\b)?""")
    } else {
        Regex("""\b(?:class|interface|object)\s+`?${Regex.escape(name)}`?(?![A-Za-z0-9_])""")
    }

private const val UNNAMED_COMPANION: String = "Companion"

// Blank only as far as the header when the first `{` opens the body (blanking goes from the start, so the result is identical);
// otherwise (`{` in a default value or a supertype argument such as `FreeSpec({ ... })`, or no body) blank all of the text.
private fun codeAfterNameOf(text: String, name: String): String? {
    val regex = headerRegexOf(name)
    val brace = text.indexOf('{')
    if (brace >= 0) {
        val afterName = afterNameIn(codeOnly(text.substring(0, brace + 1)), regex)
        if (afterName != null && headerEndIn(afterName, topLevelOf(afterName)) != null) return afterName
    }
    return afterNameIn(codeOnly(text), regex)
}

private fun afterNameIn(code: String, regex: Regex): String? =
    regex.find(code)?.let { found -> code.substring(found.range.last + 1) }

private fun supertypeListOf(rest: String): String? {
    val topLevel = topLevelOf(rest)
    val end = headerEndIn(rest, topLevel) ?: rest.length
    for (index in 0 until end) {
        if (topLevel[index] && rest[index] == ':') return rest.substring(index + 1, end)
    }
    return null
}

private fun headerEndIn(rest: String, topLevel: BooleanArray): Int? {
    for (index in rest.indices) {
        if (topLevel[index] && (rest[index] == '{' || spacedKeywordAt(rest, index, "where"))) return index
    }
    return null
}

private fun isClosingAngle(text: String, index: Int): Boolean =
    text[index] == '>' && text.getOrNull(index - 1) != '-'

private fun splitAtTopLevelCommas(list: String): List<String> {
    val topLevel = topLevelOf(list)
    val entries = mutableListOf<String>()
    var from = 0
    for ((index, char) in list.withIndex()) {
        if (char == ',' && topLevel[index]) {
            entries += list.substring(from, index)
            from = index + 1
        }
    }
    entries += list.substring(from)
    return entries
}

private fun withoutDelegation(entry: String): String {
    val topLevel = topLevelOf(entry)
    for (index in entry.indices) {
        if (topLevel[index] && spacedKeywordAt(entry, index, "by")) return entry.substring(0, index).trim()
    }
    return entry
}

private fun spacedKeywordAt(code: String, index: Int, word: String): Boolean =
    code.startsWith(word, index) &&
        code.getOrNull(index - 1)?.isWhitespace() == true &&
        code.getOrNull(index + word.length)?.isWhitespace() == true

private fun withoutConstructorCall(entry: String): String {
    if (!entry.endsWith(')')) return entry
    var depth = 0
    for (index in entry.indices.reversed()) {
        when (entry[index]) {
            ')' -> depth++
            '(' -> {
                depth--
                if (depth == 0) return entry.substring(0, index).trim()
            }
        }
    }
    return entry
}

internal fun withoutTypeArguments(type: String): String = buildString {
    var depth = 0
    for ((index, char) in type.withIndex()) {
        when {
            char == '<' -> depth++
            isClosingAngle(type, index) && depth > 0 -> depth--
            depth == 0 -> append(char)
        }
    }
}.trim()

internal fun collapseSpaces(text: String): String = text.trim().replace(Regex("""\s+"""), " ")
