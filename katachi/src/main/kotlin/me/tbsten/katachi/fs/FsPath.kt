package me.tbsten.katachi.fs

import me.tbsten.katachi.InternalKatachiApi

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
 *
 * ## Example 1: build a path and read it back
 * ```kt
 * FsPath.of("/repo//app///src").value shouldBe "/repo/app/src"
 * ```
 */
@InternalKatachiApi
public class FsPath private constructor(
    private val rootPrefix: String,
    /** The path split on `/`, outermost first. Empty for the root of an absolute path. */
    public val segments: List<String>,
) {
    /**
     * The normalized path, e.g. `/repo/app/build.gradle.kts`. `.` for the empty relative path.
     *
     * ## Example 1: normalize a raw string with mixed separators
     * ```kt
     * FsPath.of("""C:\repo\app""").value shouldBe "C:/repo/app"
     * ```
     */
    public val value: String =
        if (segments.isEmpty()) rootPrefix.ifEmpty { "." } else rootPrefix + segments.joinToString("/")

    /**
     * `true` for `/repo` and `C:/repo`, `false` for `app/src`.
     *
     * ## Example 1: tell an absolute path from a relative one
     * ```kt
     * FsPath.of("app/src").isAbsolute shouldBe false
     * ```
     */
    public val isAbsolute: Boolean get() = rootPrefix.isNotEmpty()

    /**
     * The last segment, e.g. `build.gradle.kts`. Empty at the root of an absolute path.
     *
     * ## Example 1: read the file name off a path
     * ```kt
     * FsPath.of("/repo/app/build.gradle.kts").name shouldBe "build.gradle.kts"
     * ```
     */
    public val name: String get() = segments.lastOrNull() ?: ""

    /**
     * The containing directory, or `null` when there is nothing left to walk up to.
     *
     * ## Example 1: walk up to the containing directory
     * ```kt
     * FsPath.of("/repo/app/src").parent?.value shouldBe "/repo/app"
     * ```
     */
    public val parent: FsPath? get() = if (segments.isEmpty()) null else FsPath(rootPrefix, segments.dropLast(1))

    /**
     * Appends [child], which may itself contain separators (`gradle/wrapper/gradle-wrapper.jar`).
     * `.` and `..` in [child] are resolved lexically; a `..` that would walk above the root is
     * dropped rather than reported, because no caller inside katachi builds such a path.
     *
     * ## Example 1: build a path to a file several levels down
     * ```kt
     * (FsPath.of("/repo") / "gradle/wrapper/gradle-wrapper.jar").value shouldBe
     *     "/repo/gradle/wrapper/gradle-wrapper.jar"
     * ```
     */
    public operator fun div(child: String): FsPath = FsPath(rootPrefix, appendTo(segments, child))

    /**
     * `true` when this path is [other] itself or sits below it.
     *
     * ## Example 1: check whether a path sits below a root
     * ```kt
     * FsPath.of("/repo/app/src").startsWith(FsPath.of("/repo")) shouldBe true
     * ```
     */
    public fun startsWith(other: FsPath): Boolean =
        rootPrefix == other.rootPrefix &&
            segments.size >= other.segments.size &&
            segments.subList(0, other.segments.size) == other.segments

    /**
     * This path written relative to [base], e.g. `app/src` for `/repo/app/src` under `/repo`.
     * Empty when the two are equal, `null` when this path does not sit below [base].
     *
     * ## Example 1: turn an absolute path into one relative to the project root
     * ```kt
     * FsPath.of("/repo/app/src").relativeTo(FsPath.of("/repo")) shouldBe "app/src"
     * ```
     */
    public fun relativeTo(base: FsPath): String? =
        if (!startsWith(base)) null else segments.drop(base.segments.size).joinToString("/")

    override fun equals(other: Any?): Boolean = other is FsPath && other.value == value

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = value

    /**
     * Where [FsPath] values are built from a raw string.
     *
     * ## Example 1: build a path from a raw string
     * ```kt
     * FsPath.of("/repo/app").isAbsolute shouldBe true
     * ```
     */
    public companion object {
        /**
         * Parses [raw], accepting either separator and any number of them in a row.
         *
         * ## Example 1: resolve `.` and `..` while parsing
         * ```kt
         * FsPath.of("/repo/./app/../lib").value shouldBe "/repo/lib"
         * ```
         */
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
