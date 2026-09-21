package me.tbsten.katachi.check

import me.tbsten.katachi.scan.parentPath

/**
 * A role declaration for [path], ready to paste into the definition.
 *
 * The point is that the reader — a person or an agent — does not have to go and read the
 * layout DSL to answer a report. The name is a guess, and `summary = "TODO"` says out loud
 * that the guess is not the finished declaration.
 */
internal fun roleSuggestionFor(path: String, isDirectory: Boolean): List<String> = listOf(
    "\"${suggestedRoleName(path.substringAfterLast('/'))}\" {",
    "  summary = \"TODO\"",
    "  layout {",
    "    ${layoutLineFor(path, isDirectory)}",
    "  }",
    "}",
)

/**
 * The layout line of a suggestion.
 *
 * A directory gets `ignore()` rather than an empty block: an empty block means *nothing may
 * live here*, which would turn one report into one per file inside. Someone who wants the
 * files to have roles writes them out instead, which the block above the fragment says.
 */
private fun layoutLineFor(path: String, isDirectory: Boolean): String {
    if (isDirectory) return "\"$path\" { ignore() }"
    val directory = path.parentPath()
    val file = fileCallFor(path.substringAfterLast('/'))
    // A layout key may spell out several levels, so the whole directory path is one string.
    return if (directory.isEmpty()) file else "\"$directory\" / $file"
}

/** `"GetUser".ktFile()` for `GetUser.kt`, and `"libs.versions.toml".file()` for the rest. */
private fun fileCallFor(name: String): String = when {
    name.endsWith(KTS_SUFFIX) -> "\"${name.dropLast(KTS_SUFFIX.length)}\".ktsFile()"
    name.endsWith(KT_SUFFIX) -> "\"${name.dropLast(KT_SUFFIX.length)}\".ktFile()"
    else -> "\"$name\".file()"
}

/**
 * A role name guessed from a file or directory name.
 *
 * Every run of characters a role name cannot hold splits the name, and each part is
 * capitalised, so that `libs.versions.toml` suggests `LibsVersionsToml` and
 * `tmp-experiment` suggests `TmpExperiment`. The extension of a Kotlin file is dropped
 * first, since `TokenRefresherKt` would read as a mistake.
 */
internal fun suggestedRoleName(name: String): String {
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
