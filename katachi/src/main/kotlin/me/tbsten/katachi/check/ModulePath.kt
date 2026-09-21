package me.tbsten.katachi.check

import me.tbsten.katachi.dsl.InternalKatachiApi

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
 * the same way [FsPath] is: a check must give the same answer locally and on CI.
 */
public class ModulePath private constructor(
    /** The names between the `:`, outermost first. Empty for [ROOT]. */
    public val segments: List<String>,
) {
    /** The path as katachi prints it: `":core:data"`, or `":"` for the root project. */
    public val value: String = ":" + segments.joinToString(":")

    /** Whether this is the root project, `":"`. */
    public val isRoot: Boolean get() = segments.isEmpty()

    /** The innermost name — `"data"` for `:core:data`. Empty for [ROOT]. */
    public val name: String get() = segments.lastOrNull() ?: ""

    /** The module one level up, or `null` at [ROOT]. */
    public val parent: ModulePath? get() = if (isRoot) null else ModulePath(segments.dropLast(1))

    /** The module named [name] directly below this one. */
    public fun child(name: String): ModulePath = ModulePath(segments + name)

    override fun equals(other: Any?): Boolean = other is ModulePath && other.value == value

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = value

    public companion object {
        /** The root project, `":"`. */
        public val ROOT: ModulePath = ModulePath(emptyList())

        /**
         * Reads [raw] as a module path, with or without its leading `:`.
         *
         * @throws GlobSyntaxException when [raw] is empty, has an empty segment (`":core::data"`,
         *   `":core:"`), or still holds a `*` — a pattern goes through [ModulePattern] instead.
         */
        public fun of(raw: String): ModulePath {
            if (raw.isEmpty()) {
                throw GlobSyntaxException(
                    "A module path must not be empty. Write `\":\"` for the root project.",
                )
            }
            val body = raw.removePrefix(SEPARATOR.toString())
            if (body.isEmpty()) return ROOT
            val segments = body.split(SEPARATOR)
            if (segments.any { it.isEmpty() }) {
                throw GlobSyntaxException(
                    "`$raw` has an empty module name. Two `$SEPARATOR` in a row, or a trailing " +
                        "`$SEPARATOR`, names no module.",
                )
            }
            val wildcard = segments.firstOrNull { '*' in it }
            if (wildcard != null) {
                throw GlobSyntaxException(
                    "`$raw` uses `*` in the module name `$wildcard`. A module path names one " +
                        "module; a pattern that matches several is expanded before this point.",
                )
            }
            return ModulePath(segments)
        }

        /** Gradle's module path separator, the same character katachi's glob splits on. */
        private const val SEPARATOR: Char = ':'
    }
}

/**
 * A module path as written in a layout key, wildcards and all: `":feature:*"`.
 *
 * It is the module-path half of [Glob] — the same translation to a regular expression, with
 * `:` for a separator instead of `/`, so `*` and `**` cannot come to mean two different
 * things on the two sides of the DSL. On top of the glob it adds what only a module path
 * needs: a leading `:` is optional, `":"` names the root project, and `**` is restricted to
 * the last segment so that the index of every captured wildcard is the same for every match.
 */
@InternalKatachiApi
public class ModulePattern private constructor(
    /** The pattern with its leading `:` filled in, e.g. `":feature:*"`. */
    public val pattern: String,
    /** `null` for `":"`, which names the root project and matches nothing else. */
    private val glob: Glob?,
) {
    /** Whether the pattern holds a `*` or a `**`, which is what makes a declaration optional. */
    public val hasWildcard: Boolean get() = glob?.hasWildcard == true

    /** The single module named, when there is no wildcard to expand. */
    public val literalPath: ModulePath?
        get() = if (hasWildcard) null else ModulePath.of(unescape(pattern))

    /** Whether [module] is one of the modules this pattern names. */
    public fun matches(module: ModulePath): Boolean = match(module) != null

    /**
     * What the wildcards captured when [module] matched, or `null` when it did not.
     *
     * One element per `*`, and one **per level** for the trailing `**`, so `":feature:**"`
     * against `:feature:hoge:fuga` captures `["hoge", "fuga"]` and against `:feature` itself
     * captures nothing.
     */
    public fun match(module: ModulePath): List<String>? {
        val glob = glob ?: return if (module.isRoot) emptyList() else null
        return glob.match(module.value)?.wildcards
    }

    override fun equals(other: Any?): Boolean = other is ModulePattern && other.pattern == pattern

    override fun hashCode(): Int = pattern.hashCode()

    override fun toString(): String = "ModulePattern($pattern)"

    public companion object {
        /**
         * Translates [raw] into a pattern, filling in the leading `:` when it was left out.
         *
         * @throws GlobSyntaxException when [raw] is empty or cannot be read as a glob, or when
         *   it uses `**` anywhere but as its last segment, or more than once.
         */
        public fun compile(raw: String): ModulePattern {
            if (raw.isEmpty()) {
                throw GlobSyntaxException(
                    "A module path must not be empty. Write `\":\"` for the root project.",
                )
            }
            val normalized = if (raw.startsWith(Glob.MODULE_SEPARATOR)) raw else "${Glob.MODULE_SEPARATOR}$raw"
            // `":"` is the root project. It is not a glob — `Glob.compile` would read the
            // separator as the start of an empty segment.
            if (normalized == Glob.MODULE_SEPARATOR.toString()) return ModulePattern(normalized, null)
            val glob = Glob.compile(normalized, Glob.MODULE_SEPARATOR)
            glob.requireAtMostOneTrailingDoubleStar()
            return ModulePattern(normalized, glob)
        }

        /** Drops the backslashes the glob compiler would have read as escapes. */
        private fun unescape(pattern: String): String {
            if ('\\' !in pattern) return pattern
            val result = StringBuilder(pattern.length)
            var index = 0
            while (index < pattern.length) {
                val character = pattern[index]
                if (character == '\\' && index + 1 < pattern.length) {
                    result.append(pattern[index + 1])
                    index += 2
                } else {
                    result.append(character)
                    index++
                }
            }
            return result.toString()
        }
    }
}
