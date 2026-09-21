package me.tbsten.katachi.check

import me.tbsten.katachi.dsl.InternalKatachiApi

/**
 * A path in the shape katachi works with: always `/` separated, never carrying `.` or `..`
 * segments, never ending with a separator.
 *
 * The type exists so that katachi never passes an OS specific string around. The real file
 * system implementation is the only place that knows about `\`, and it converts on the way
 * in ([of]) and on the way out (`FsPath.value` is handed back to `java.io.File`, which
 * accepts `/` on every platform katachi supports).
 *
 * Two paths are equal when [value] is equal, so comparison is case sensitive even on a file
 * system that is not. This is deliberate: a check must not pass on macOS and fail on CI.
 */
@InternalKatachiApi
public class FsPath private constructor(
    private val rootPrefix: String,
    /** The path split on `/`, outermost first. Empty for the root of an absolute path. */
    public val segments: List<String>,
) {
    /** The normalized path, e.g. `/repo/app/build.gradle.kts`. `.` for the empty relative path. */
    public val value: String =
        if (segments.isEmpty()) rootPrefix.ifEmpty { "." } else rootPrefix + segments.joinToString("/")

    /** `true` for `/repo` and `C:/repo`, `false` for `app/src`. */
    public val isAbsolute: Boolean get() = rootPrefix.isNotEmpty()

    /** The last segment, e.g. `build.gradle.kts`. Empty at the root of an absolute path. */
    public val name: String get() = segments.lastOrNull() ?: ""

    /** The containing directory, or `null` when there is nothing left to walk up to. */
    public val parent: FsPath? get() = if (segments.isEmpty()) null else FsPath(rootPrefix, segments.dropLast(1))

    /**
     * Appends [child], which may itself contain separators (`gradle/wrapper/gradle-wrapper.jar`).
     * `.` and `..` in [child] are resolved lexically; a `..` that would walk above the root is
     * dropped rather than reported, because no caller inside katachi builds such a path.
     */
    public operator fun div(child: String): FsPath = FsPath(rootPrefix, appendTo(segments, child))

    /** `true` when this path is [other] itself or sits below it. */
    public fun startsWith(other: FsPath): Boolean =
        rootPrefix == other.rootPrefix &&
            segments.size >= other.segments.size &&
            segments.subList(0, other.segments.size) == other.segments

    /**
     * This path written relative to [base], e.g. `app/src` for `/repo/app/src` under `/repo`.
     * Empty when the two are equal, `null` when this path does not sit below [base].
     */
    public fun relativeTo(base: FsPath): String? =
        if (!startsWith(base)) null else segments.drop(base.segments.size).joinToString("/")

    override fun equals(other: Any?): Boolean = other is FsPath && other.value == value

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = value

    public companion object {
        /** Parses [raw], accepting either separator and any number of them in a row. */
        public fun of(raw: String): FsPath {
            val normalized = raw.replace('\\', '/')
            val prefix = when {
                normalized.length >= 3 &&
                    normalized[0].isLetter() &&
                    normalized[1] == ':' &&
                    normalized[2] == '/' -> normalized.substring(0, 3)

                normalized.startsWith("/") -> "/"
                else -> ""
            }
            return FsPath(prefix, appendTo(emptyList(), normalized.substring(prefix.length)))
        }

        private fun appendTo(base: List<String>, text: String): List<String> {
            val result = base.toMutableList()
            for (part in text.split('/', '\\')) {
                when {
                    part.isEmpty() || part == "." -> Unit
                    part == ".." -> if (result.isNotEmpty()) result.removeAt(result.lastIndex)
                    else -> result += part
                }
            }
            return result
        }
    }
}
