package me.tbsten.katachi.check.internal

import me.tbsten.katachi.dsl.Architecture

internal fun roleSuggestionFor(
    path: String,
    isDirectory: Boolean,
    roleName: String? = null,
): List<String> = listOf(
    "\"${roleName ?: suggestedRoleName(path.substringAfterLast('/'))}\".role {",
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

/**
 * Names the roles of one report's "Add a new role" fragments, each pasted at the root of
 * `architecture { }`: a name the root already holds, or one an earlier fragment of the same
 * report was given, would make the pasted fragment a duplicate declaration.
 */
internal class RoleNameSuggestions(taken: Collection<String>) {
    private val taken: MutableSet<String> = taken.toHashSet()

    fun nameFor(path: String): String = candidatesFor(path).first { it !in taken }.also { taken += it }
}

// A group, a role and a template set share one namespace at the root, so every one of them takes a name there.
internal fun Architecture.rootNames(): List<String> =
    groups.map { it.name } + roles.map { it.name } + templateSets.map { it.name }

// The plain name, then the parent directory in front of it (with "Dot" for a leading dot, which is what tells
// `.gradle` from `gradle`), then that with a number: the same path and the same taken names always give one answer.
private fun candidatesFor(path: String): Sequence<String> {
    val name = path.substringAfterLast('/')
    val plain = suggestedRoleName(name)
    val dot = if (name.startsWith('.')) "Dot" else ""
    val qualified = (pascalCase(path.parentPath().substringAfterLast('/')) + dot + pascalCase(withoutKotlinSuffix(name)))
        .takeIf { it.startsWithLetter() }
    val stem = qualified ?: plain
    return sequenceOf(plain) + listOfNotNull(qualified) + generateSequence(2) { it + 1 }.map { "$stem$it" }
}

private fun fileCallFor(name: String): String = when {
    name.endsWith(KTS_SUFFIX) -> "\"${name.dropLast(KTS_SUFFIX.length)}\".ktsFile()"
    name.endsWith(KT_SUFFIX) -> "\"${name.dropLast(KT_SUFFIX.length)}\".ktFile()"
    else -> "\"$name\".file()"
}

private fun suggestedRoleName(name: String): String {
    val suggestion = pascalCase(withoutKotlinSuffix(name))
    // A role name has to start with a letter. A name made only of digits or symbols leaves
    // nothing to guess from, so the fragment says so rather than suggesting something that
    // would not compile.
    return if (suggestion.startsWithLetter()) suggestion else "TODO"
}

private fun withoutKotlinSuffix(name: String): String = when {
    name.endsWith(KTS_SUFFIX) -> name.dropLast(KTS_SUFFIX.length)
    name.endsWith(KT_SUFFIX) -> name.dropLast(KT_SUFFIX.length)
    else -> name
}

private fun pascalCase(text: String): String =
    text.split(NON_IDENTIFIER).filter { it.isNotEmpty() }.joinToString("") { word -> word.replaceFirstChar { it.uppercaseChar() } }

private fun String.startsWithLetter(): Boolean = firstOrNull()?.isLetter() == true

private const val KT_SUFFIX: String = ".kt"
private const val KTS_SUFFIX: String = ".kts"
private val NON_IDENTIFIER: Regex = Regex("[^A-Za-z0-9]+")
