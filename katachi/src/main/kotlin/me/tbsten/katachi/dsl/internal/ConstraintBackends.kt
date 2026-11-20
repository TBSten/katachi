package me.tbsten.katachi.dsl.internal

import me.tbsten.katachi.dsl.FileConstraint

// :katachi cannot import the backends, so a constraint is told by its class name; the more specific
// prefix comes first (ktFileContent lives inside the Konsist package).

internal const val KT_FILE_CONTENT_BACKEND: String = "ktFileContent"

private const val CUSTOM_BACKEND: String = "custom"

private val BACKEND_PREFIXES: List<Pair<String, String>> = listOf(
    "me.tbsten.katachi.konsist.declarations." to KT_FILE_CONTENT_BACKEND,
    "me.tbsten.katachi.konsist." to "konsist",
    "me.tbsten.katachi.konture." to "konture",
)

internal fun backendOf(className: String): String =
    BACKEND_PREFIXES.firstOrNull { (prefix, _) -> className.startsWith(prefix) }?.second ?: CUSTOM_BACKEND

internal fun backendOf(check: FileConstraint): String = backendOf(check::class.java.name)

// The block a constraint of that backend is written with, as a message names it.
internal fun kindSpellingOf(check: FileConstraint): String = when (val backend = backendOf(check)) {
    CUSTOM_BACKEND -> CUSTOM_KIND_SPELLING
    else -> "$backend { }"
}

internal const val CUSTOM_KIND_SPELLING: String = "fileConstraint { }"
