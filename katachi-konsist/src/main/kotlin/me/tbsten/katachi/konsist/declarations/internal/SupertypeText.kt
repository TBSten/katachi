package me.tbsten.katachi.konsist.declarations.internal

/*
 * The supertypes of a class, an interface or an object, read off the declaration's own text.
 *
 * Not `KoParentProvider.parents()`, and deliberately so. Konsist 0.17.3 ends that call with
 * `distinctBy { it.sourceDeclaration }`, and resolving a source declaration searches a scope of the
 * whole project Konsist infers -- which ran the test JVM of this repository out of memory. The
 * header is short and regular enough to read directly: skip to the declaration's own name, step
 * over its type parameters and primary constructor, and take what the `:` lists up to the body or
 * a `where` clause. The same reading as `architecture-test`'s `directSupertypeNamesOf`, which
 * lives in another module and so cannot be shared. A body can be long and every nested class reads
 * its own header, so the body is left alone wherever the first `{` shows where the header ends.
 */

/**
 * The supertypes the declaration [text] lists, where [text] is its text with its KDoc and
 * annotations taken off, and [name] its name (`Companion` for an unnamed companion object).
 *
 * The header is read with its comments and the insides of its string literals blanked out
 * (`codeOnly`), so that `Base(), // the base` and `Exception(":)")` read as the code they are.
 */
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

/**
 * [text] without the comments it starts with (its KDoc) and without the first occurrence of each
 * of [annotationTexts], so that a `class X` in a comment or an argument is not taken for the header.
 */
internal fun headerTextOf(text: String, annotationTexts: List<String>): String {
    var header = withoutLeadingComments(text)
    for (annotation in annotationTexts) {
        val index = header.indexOf(annotation)
        if (index >= 0) header = header.removeRange(index, index + annotation.length)
    }
    return header
}

/** [text] without the block and line comments, and the white space, it starts with. */
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

/** The keyword and the name of the declaration: `class HomeViewModel`, `companion object`. */
private fun headerRegexOf(name: String): Regex =
    if (name == UNNAMED_COMPANION) {
        Regex("""\bcompanion\s+object\b(?:\s+`?${Regex.escape(name)}`?\b)?""")
    } else {
        Regex("""\b(?:class|interface|object)\s+`?${Regex.escape(name)}`?(?![A-Za-z0-9_])""")
    }

/** The name Kotlin gives a companion object written without one. */
private const val UNNAMED_COMPANION: String = "Companion"

/**
 * The code (`codeOnly`) after the name of the declaration [text], or `null` when the name is not
 * found. A body can be long, so [text] is blanked only as far as the header when that is easy to tell.
 *
 * In most classes the first `{` opens the body. It does when the code up to it holds the end of the
 * header, and then only that part is blanked: exactly as it is within the whole text, because
 * blanking goes from the start and what follows a `{` changes nothing at or before it. When it does
 * not (the `{` is in a default value or in an argument of a supertype, as in `FreeSpec({ ... })`, or
 * there is no body), all of [text] is blanked.
 */
private fun codeAfterNameOf(text: String, name: String): String? {
    val regex = headerRegexOf(name)
    val brace = text.indexOf('{')
    if (brace >= 0) {
        val afterName = afterNameIn(codeOnly(text.substring(0, brace + 1)), regex)
        if (afterName != null && headerEndIn(afterName, topLevelOf(afterName)) != null) return afterName
    }
    return afterNameIn(codeOnly(text), regex)
}

/** What follows the first match of [regex] in [code], or `null` when there is none. */
private fun afterNameIn(code: String, regex: Regex): String? =
    regex.find(code)?.let { found -> code.substring(found.range.last + 1) }

/**
 * What follows the header's `:` in [rest], the text after a declaration's name, or `null` when it
 * lists no supertype. Brackets are skipped so a constructor parameter's `:` is never taken; the
 * list ends where the header does.
 */
private fun supertypeListOf(rest: String): String? {
    val topLevel = topLevelOf(rest)
    val end = headerEndIn(rest, topLevel) ?: rest.length
    for (index in 0 until end) {
        if (topLevel[index] && rest[index] == ':') return rest.substring(index + 1, end)
    }
    return null
}

/**
 * The index in [rest], the text after a declaration's name, where its header ends: the `{` of the
 * body or the `where` clause, whichever comes first outside every bracket. `null` when neither is
 * there. [topLevel] is `topLevelOf(rest)`.
 */
private fun headerEndIn(rest: String, topLevel: BooleanArray): Int? {
    for (index in rest.indices) {
        if (topLevel[index] && (rest[index] == '{' || spacedKeywordAt(rest, index, "where"))) return index
    }
    return null
}

/** Whether the `>` at [index] closes a `<`, rather than ending the arrow of a function type. */
private fun isClosingAngle(text: String, index: Int): Boolean =
    text[index] == '>' && text.getOrNull(index - 1) != '-'

/** [list] split at the commas that are not inside `<...>` or `(...)`. */
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

/** `Handler by Noop` is `Handler`: the delegate is not part of the type. */
private fun withoutDelegation(entry: String): String {
    val topLevel = topLevelOf(entry)
    for (index in entry.indices) {
        if (topLevel[index] && spacedKeywordAt(entry, index, "by")) return entry.substring(0, index).trim()
    }
    return entry
}

/** Whether [word] starts at [index] of [code] with white space (a line break too) on both sides. */
private fun spacedKeywordAt(code: String, index: Int, word: String): Boolean =
    code.startsWith(word, index) &&
        code.getOrNull(index - 1)?.isWhitespace() == true &&
        code.getOrNull(index + word.length)?.isWhitespace() == true

/** `ViewModel()` and `Base(id)` are `ViewModel` and `Base`: the constructor call is not part of the type. */
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

/** [type] with every `<...>` taken out: `Repository<User>` is `Repository`. */
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

/** [text] with every run of white space made one space, and trimmed. */
internal fun collapseSpaces(text: String): String = text.trim().replace(Regex("""\s+"""), " ")
