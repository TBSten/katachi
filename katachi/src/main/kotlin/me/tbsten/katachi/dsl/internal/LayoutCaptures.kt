package me.tbsten.katachi.dsl.internal

/**
 * The names one declaration of a [me.tbsten.katachi.dsl.LayoutEntry] gave its wildcards: the
 * `capture("...")` levels of its path, and the names of the module key it sits in.
 *
 * The check never reads this — a named wildcard is checked exactly as a plain `*` is. It exists
 * for template generation, which fills the named levels in with `--arg` values to work out
 * where a new file goes.
 *
 * An empty instance ([NONE]) stands for a declaration that named nothing. It only ever appears
 * next to another variant: an entry no declaration named anything on has no variants at all.
 */
internal data class LayoutCaptures(
    /** The named levels of the entry's path, outermost first. */
    val pathCaptures: List<PathCapture>,
    /** The names of the module key the entry sits in, or `null` when that key named nothing. */
    val moduleCapture: ModuleCapture?,
) {
    /** Every name this declaration takes a value by: the module's first, then the path's. */
    val names: List<String>
        get() = moduleCapture?.names.orEmpty() + pathCaptures.map { it.name }

    val isEmpty: Boolean
        get() = pathCaptures.isEmpty() && moduleCapture == null

    companion object {
        val NONE: LayoutCaptures = LayoutCaptures(pathCaptures = emptyList(), moduleCapture = null)
    }
}

/** A `capture(name)` level: the [segmentIndex]-th level (0-based) of the entry's path is a `*` named [name]. */
internal data class PathCapture(val segmentIndex: Int, val name: String)

/** The names a `":...:${capture("...")}".module { }` key gave its `*`s, in order. */
internal data class ModuleCapture(
    /** The key as katachi prints it, `":feature:*"`. */
    val modulePattern: String,
    val names: List<String>,
)
