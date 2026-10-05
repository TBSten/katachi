package me.tbsten.katachi.dsl.internal

/**
 * The names one declaration of a [me.tbsten.katachi.dsl.LayoutEntry] gave its wildcards: the
 * `capture("...")` occurrences of its path, and the names of the module key it sits in.
 *
 * A name used once is checked exactly as a plain `*` is; the check reads this only for a
 * declaration that [isBound] -- one that wrote the same name on two levels, or derived a level
 * from another with `capture("x").pascalCase` -- and then only to compare the values the
 * levels read. Template generation reads it to fill the named levels in with `--arg` values.
 *
 * An empty instance ([NONE]) stands for a declaration that named nothing. It only ever appears
 * next to another variant: an entry no declaration named anything on has no variants at all.
 */
internal data class LayoutCaptures(
    /** Every occurrence on the entry's path, outermost first and left to right within a level. */
    val pathCaptures: List<PathCapture>,
    /** The names of the module key the entry sits in, or `null` when that key named nothing. */
    val moduleCapture: ModuleCapture?,
) {
    /**
     * Every name this declaration takes a `--arg` value by, once each: the module's first, then
     * the path's. A derived occurrence has no name of its own and is left out.
     */
    val names: List<String>
        get() = (moduleCapture?.names.orEmpty() + pathCaptures.filter { it.transform == null }.map { it.name }).distinct()

    /**
     * Whether the check has to read values off a file this declaration matches: a plain name
     * written on two levels (the module key's names counted) is a claim that the two read the
     * same, and a derived occurrence is a claim about its plain capture's value.
     */
    val isBound: Boolean
        get() {
            if (pathCaptures.any { it.transform != null }) return true
            val plain = moduleCapture?.names.orEmpty() + pathCaptures.map { it.name }
            return plain.size != plain.distinct().size
        }

    val isEmpty: Boolean
        get() = pathCaptures.isEmpty() && moduleCapture == null

    companion object {
        val NONE: LayoutCaptures = LayoutCaptures(pathCaptures = emptyList(), moduleCapture = null)
    }
}

/**
 * One `capture(name)` occurrence: the [wildcardIndex]-th `*` (0-based, counted over every
 * unescaped `*` of the level, named or not) of the [segmentIndex]-th level (0-based) of the
 * entry's path. [transform] is the conversion of a derived occurrence, `null` for a plain one.
 */
internal data class PathCapture(
    val segmentIndex: Int,
    val wildcardIndex: Int,
    val name: String,
    val transform: CaptureTransform?,
)

/** The names a `":...:${capture("...")}".module { }` key gave its `*`s, in order. */
internal data class ModuleCapture(
    /** The key as katachi prints it, `":feature:*"`. */
    val modulePattern: String,
    val names: List<String>,
)
