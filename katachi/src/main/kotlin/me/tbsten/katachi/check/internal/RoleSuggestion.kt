package me.tbsten.katachi.check.internal

internal fun roleSuggestionFor(
    path: String,
    isDirectory: Boolean,
    roleName: String = suggestedRoleName(path.substringAfterLast('/')),
): List<String> = listOf(
    "\"$roleName\".role {",
    "  summary = \"TODO\"",
    "  layout {",
    "    ${layoutLineFor(path, isDirectory)}",
    "  }",
    "}",
)

// A directory gets ignore(), not an empty block: an empty block means nothing may live there and would report every file inside.
internal fun layoutLineFor(path: String, isDirectory: Boolean): String {
    if (isDirectory) return "\"$path\".ignore()"
    val directory = path.parentPath()
    val file = fileCallFor(path.substringAfterLast('/'))
    // A layout key may spell out several levels, so the whole directory path is one string.
    return if (directory.isEmpty()) file else "\"$directory\" / $file"
}

private fun fileCallFor(name: String): String = when {
    name.endsWith(KTS_SUFFIX) -> "\"${name.dropLast(KTS_SUFFIX.length)}\".ktsFile()"
    name.endsWith(KT_SUFFIX) -> "\"${name.dropLast(KT_SUFFIX.length)}\".ktFile()"
    else -> "\"$name\".file()"
}

private fun suggestedRoleName(name: String): String {
    val base = when {
        name.endsWith(KTS_SUFFIX) -> name.dropLast(KTS_SUFFIX.length)
        name.endsWith(KT_SUFFIX) -> name.dropLast(KT_SUFFIX.length)
        else -> name
    }
    val words = base.split(NON_IDENTIFIER).filter { it.isNotEmpty() }
    val suggestion = words.joinToString("") { word -> word.replaceFirstChar { it.uppercaseChar() } }
    // A role name has to start with a letter. A name made only of digits or symbols leaves
    // nothing to guess from, so the fragment says so rather than suggesting something that
    // would not compile.
    return if (suggestion.isEmpty() || !suggestion.first().isLetter()) "TODO" else suggestion
}

private const val KT_SUFFIX: String = ".kt"
private const val KTS_SUFFIX: String = ".kts"
private val NON_IDENTIFIER: Regex = Regex("[^A-Za-z0-9]+")
