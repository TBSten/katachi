package me.tbsten.katachi.ide.internal

/*
 * Which backend wrote a constraint, told from the class name of its `FileConstraint`. `:katachi`
 * cannot import the backends -- the Gradle dependency runs the other way -- so the class name is
 * all it has. The more specific prefix comes first: `ktFileContent { }` lives inside the Konsist
 * backend's package.
 */

private val BACKEND_PREFIXES: List<Pair<String, String>> = listOf(
    "me.tbsten.katachi.konsist.declarations." to "ktFileContent",
    "me.tbsten.katachi.konsist." to "konsist",
    "me.tbsten.katachi.konture." to "konture",
)

/** The backend that wrote a constraint of [className]: `ktFileContent`, `konsist`, `konture`, or `custom`. */
internal fun backendOf(className: String): String =
    BACKEND_PREFIXES.firstOrNull { (prefix, _) -> className.startsWith(prefix) }?.second ?: "custom"
