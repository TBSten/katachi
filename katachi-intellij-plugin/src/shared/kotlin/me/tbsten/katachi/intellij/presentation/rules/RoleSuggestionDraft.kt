package me.tbsten.katachi.intellij.presentation.rules

// Must give the same text as the report of katachi's check under an UnexpectedFile (`RoleSuggestion.kt`), so the notification and the report agree.
// TODO: for a file under a directory no role knows, the report says UnexpectedDirectory and suggests `ignore()`ing the directory; this draft stays about the file.
internal fun roleSuggestionDraftFor(relativePath: String): String = roleSuggestionLinesFor(relativePath).joinToString("\n")

internal fun suggestedRoleNameFor(relativePath: String): String {
    val name = relativePath.substringAfterLast('/')
    val base = when {
        name.endsWith(KTS_SUFFIX) -> name.dropLast(KTS_SUFFIX.length)
        name.endsWith(KT_SUFFIX) -> name.dropLast(KT_SUFFIX.length)
        else -> name
    }
    val suggestion = base.split(NON_IDENTIFIER).filter { it.isNotEmpty() }.joinToString("") { word -> word.replaceFirstChar { it.uppercaseChar() } }
    // A role name starts with a letter; nothing to guess from otherwise.
    return if (suggestion.isEmpty() || !suggestion.first().isLetter()) "TODO" else suggestion
}

private fun roleSuggestionLinesFor(path: String): List<String> = listOf(
    "\"${suggestedRoleNameFor(path)}\".role {",
    "  summary = \"TODO\"",
    "  layout {",
    "    ${layoutLineFor(path)}",
    "  }",
    "}",
)

private fun layoutLineFor(path: String): String {
    val directory = path.substringBeforeLast('/', missingDelimiterValue = "")
    val name = path.substringAfterLast('/')
    val file = when {
        name.endsWith(KTS_SUFFIX) -> "\"${name.dropLast(KTS_SUFFIX.length)}\".ktsFile()"
        name.endsWith(KT_SUFFIX) -> "\"${name.dropLast(KT_SUFFIX.length)}\".ktFile()"
        else -> "\"$name\".file()"
    }
    return if (directory.isEmpty()) file else "\"$directory\" / $file"
}

private const val KT_SUFFIX = ".kt"
private const val KTS_SUFFIX = ".kts"
private val NON_IDENTIFIER = Regex("[^A-Za-z0-9]+")
