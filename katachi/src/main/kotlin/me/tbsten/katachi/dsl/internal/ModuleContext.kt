package me.tbsten.katachi.dsl.internal

/** The module a block is being evaluated for, or `null` when there is none. */
internal class ModuleContext(
    /** The module path as katachi prints it, `":feature:home"`. */
    val modulePath: String,
    /** What the key's wildcards captured for this module. */
    val wildcards: List<String>,
    /**
     * The names the key's `capture("...")` tokens gave its `*`s, in order, or `null` for a key
     * that named none of them. An entry may itself be `null` when only some of the key's `*`s
     * were named.
     */
    val captureNames: List<String?>? = null,
) {
    /**
     * [wildcards] by name. Only the `*`s are named, and a trailing `**` is the last wildcard, so
     * the first names line up with the first values one for one; a `*` left unnamed contributes
     * no entry.
     */
    val captures: Map<String, String>
        get() = captureNames.orEmpty().zip(wildcards)
            .mapNotNull { (name, value) -> name?.let { it to value } }
            .toMap()
}
