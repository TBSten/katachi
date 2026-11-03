package me.tbsten.katachi.dsl.internal

// The check reads values only for a bound declaration: a name on two levels, or a derived level.
internal data class LayoutCaptures(
    val pathCaptures: List<PathCapture>,
    val moduleCapture: ModuleCapture?,
) {
    // A derived occurrence has no name of its own and is left out.
    val names: List<String>
        get() = (moduleCapture?.names.orEmpty() + pathCaptures.filter { it.transform == null }.map { it.name }).distinct()

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

// transform is null for a plain occurrence; wildcardIndex counts every unescaped `*` of the level, named or not.
internal data class PathCapture(
    val segmentIndex: Int,
    val wildcardIndex: Int,
    val name: String,
    val transform: CaptureTransform?,
)

internal data class ModuleCapture(
    val modulePattern: String,
    val names: List<String>,
)
