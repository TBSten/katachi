package me.tbsten.katachi.ide.internal

// :katachi cannot import the backends, so a constraint is told by its class name; the more specific
// prefix comes first (ktFileContent lives inside the Konsist package).

private val BACKEND_PREFIXES: List<Pair<String, String>> = listOf(
    "me.tbsten.katachi.konsist.declarations." to "ktFileContent",
    "me.tbsten.katachi.konsist." to "konsist",
    "me.tbsten.katachi.konture." to "konture",
)

internal fun backendOf(className: String): String =
    BACKEND_PREFIXES.firstOrNull { (prefix, _) -> className.startsWith(prefix) }?.second ?: "custom"
