package me.tbsten.katachi.dsl.internal

/** The module a block is being evaluated for, or `null` when there is none. */
internal class ModuleContext(
    /** The module path as katachi prints it, `":feature:home"`. */
    val modulePath: String,
    /** What the key's wildcards captured for this module. */
    val wildcards: List<String>,
    /**
     * The names the key gave its `*`s, in order, or `null` for a key written without names.
     * See `"...".module(capture = ...)`.
     */
    val captureNames: List<String>? = null,
) {
    /**
     * [wildcards] by name. Only the `*`s are named, and a trailing `**` is the last wildcard, so
     * the first names line up with the first values one for one.
     */
    val captures: Map<String, String>
        get() = captureNames.orEmpty().zip(wildcards).toMap()
}
