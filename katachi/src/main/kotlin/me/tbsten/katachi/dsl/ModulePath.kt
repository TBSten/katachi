package me.tbsten.katachi.dsl

import me.tbsten.katachi.dsl.internal.GlobProblem

/**
 * A Gradle module path with no wildcard left in it: `:core:data`.
 *
 * The leading `:` is part of how a path prints, never part of how it has to be written: [of]
 * reads `":core:data"` and `"core:data"` as the same module, because both spellings turn up
 * in real build files and a layout key is written by a person. The root project is [ROOT],
 * which prints as `":"` and has no segments at all.
 *
 * A module path says nothing about where the module lives. That mapping is
 * [ModuleResolver]'s, and only the default one happens to spell `:core:data` as `core/data`.
 *
 * Two module paths are equal when their segments are equal, so comparison is case sensitive
 * the same way [me.tbsten.katachi.dsl.files.FsPath] is: a check must give the same answer locally and on CI.
 *
 * ## Example 1: build one from a string and read it back
 * ```kt
 * val data = ModulePath.of("core:data")    // the leading `:` may be left out
 * data.value shouldBe ":core:data"
 * data.segments shouldContainExactly listOf("core", "data")
 * data.name shouldBe "data"
 * data.parent shouldBe ModulePath.of(":core")
 * ModulePath.of(":core").child("data") shouldBe data
 * ModulePath.of(":") shouldBe ModulePath.ROOT
 * ModulePath.ROOT.isRoot shouldBe true
 * ```
 */
public class ModulePath private constructor(
    /**
     * The names between the `:`, outermost first. Empty for [ROOT].
     */
    public val segments: List<String>,
) {
    /**
     * The path as katachi prints it: `":core:data"`, or `":"` for the root project.
     */
    public val value: String = ":" + segments.joinToString(":")

    /**
     * Whether this is the root project, `":"`.
     */
    public val isRoot: Boolean get() = segments.isEmpty()

    /**
     * The innermost name — `"data"` for `:core:data`. Empty for [ROOT].
     */
    public val name: String get() = segments.lastOrNull() ?: ""

    /**
     * The module one level up, or `null` at [ROOT].
     */
    public val parent: ModulePath? get() = if (isRoot) null else ModulePath(segments.dropLast(1))

    /**
     * The module named [name] directly below this one.
     */
    public fun child(name: String): ModulePath = ModulePath(segments + name)

    /** Equal when both have the same [value][ModulePath.value]. */
    override fun equals(other: Any?): Boolean = other is ModulePath && other.value == value

    /** Hashes [value][ModulePath.value] alone, to agree with [equals]. */
    override fun hashCode(): Int = value.hashCode()

    /** The path itself, [value][ModulePath.value], such as `:core:data`. */
    override fun toString(): String = value

    /**
     * Where [ModulePath] instances come from: [of] parses a raw string, and [ROOT] names the
     * root project.
     *
     * ## Example 1: build a path and refer to the root project
     * ```kt
     * val module = ModulePath.of(":core:data")
     * val root = ModulePath.ROOT
     * ```
     */
    public companion object {
        /**
         * The root project, `":"`.
         */
        public val ROOT: ModulePath = ModulePath(emptyList())

        /**
         * Reads [raw] as a module path, with or without its leading `:`.
         *
         * @throws KatachiGlobSyntaxException when [raw] is empty, has an empty segment
         *   (`":core::data"`, `":core:"`), or still holds a `*`: this is one module's path, not a
         *   pattern.
         */
        public fun of(raw: String): ModulePath {
            if (raw.isEmpty()) throw KatachiGlobSyntaxException(raw, GlobProblem.EmptyModulePath)
            val body = raw.removePrefix(SEPARATOR.toString())
            if (body.isEmpty()) return ROOT
            val segments = body.split(SEPARATOR)
            if (segments.any { it.isEmpty() }) {
                throw KatachiGlobSyntaxException(raw, GlobProblem.EmptyModuleName(SEPARATOR))
            }
            val wildcard = segments.firstOrNull { '*' in it }
            if (wildcard != null) {
                throw KatachiGlobSyntaxException(raw, GlobProblem.WildcardInModuleName(wildcard))
            }
            return ModulePath(segments)
        }

        private const val SEPARATOR: Char = ':'
    }
}
