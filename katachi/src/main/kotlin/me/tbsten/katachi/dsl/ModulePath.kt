package me.tbsten.katachi.dsl

import me.tbsten.katachi.InternalKatachiApi

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
 * the same way [me.tbsten.katachi.fs.FsPath] is: a check must give the same answer locally and on CI.
 *
 * ## Example 1: build one from a string and read it back
 * ```kt
 * ModulePath.of(":core:data").value shouldBe ":core:data"
 * ```
 */
public class ModulePath private constructor(
    /**
     * The names between the `:`, outermost first. Empty for [ROOT].
     *
     * ## Example 1: read out the levels in order
     * ```kt
     * ModulePath.of(":core:data:remoteApi").segments shouldContainExactly
     *     listOf("core", "data", "remoteApi")
     * ```
     */
    public val segments: List<String>,
) {
    /**
     * The path as katachi prints it: `":core:data"`, or `":"` for the root project.
     *
     * ## Example 1: check the printed form
     * ```kt
     * ModulePath.of("core:data").value shouldBe ":core:data"
     * ```
     */
    public val value: String = ":" + segments.joinToString(":")

    /**
     * Whether this is the root project, `":"`.
     *
     * ## Example 1: tell the root project apart from the rest
     * ```kt
     * ModulePath.ROOT.isRoot shouldBe true
     * ```
     */
    public val isRoot: Boolean get() = segments.isEmpty()

    /**
     * The innermost name — `"data"` for `:core:data`. Empty for [ROOT].
     *
     * ## Example 1: read the innermost name
     * ```kt
     * ModulePath.of(":core:data").name shouldBe "data"
     * ```
     */
    public val name: String get() = segments.lastOrNull() ?: ""

    /**
     * The module one level up, or `null` at [ROOT].
     *
     * ## Example 1: walk up one level
     * ```kt
     * ModulePath.of(":core:data").parent shouldBe ModulePath.of(":core")
     * ```
     */
    public val parent: ModulePath? get() = if (isRoot) null else ModulePath(segments.dropLast(1))

    /**
     * The module named [name] directly below this one.
     *
     * ## Example 1: build a direct child
     * ```kt
     * ModulePath.of(":core").child("data") shouldBe ModulePath.of(":core:data")
     * ```
     */
    public fun child(name: String): ModulePath = ModulePath(segments + name)

    override fun equals(other: Any?): Boolean = other is ModulePath && other.value == value

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = value

    public companion object {
        /**
         * The root project, `":"`.
         *
         * ## Example 1: refer to the root project
         * ```kt
         * ModulePath.of(":") shouldBe ModulePath.ROOT
         * ```
         */
        public val ROOT: ModulePath = ModulePath(emptyList())

        /**
         * Reads [raw] as a module path, with or without its leading `:`.
         *
         * @throws KatachiGlobSyntaxException when [raw] is empty, has an empty segment
         *   (`":core::data"`, `":core:"`), or still holds a `*` — a pattern goes through
         *   [ModulePattern] instead.
         *
         * ## Example 1: read a path with or without its leading `:`
         * ```kt
         * ModulePath.of(":core:data") shouldBe ModulePath.of("core:data")
         * ```
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

    /**
     * One [WILDCARD_PLACEHOLDER] per wildcard, in pattern order, so `":feature:*"` reads as
     * `["<name>"]`.
     *
     * [match] answers the same shape filled in with a module's own names. This is what stands
     * in their place when there is no module to have matched, which is the case for a layout
     * read without a file system: see [ModuleIndex.targetsOf].
     *
     * A `**` contributes one entry rather than one per level, because how many levels it
     * would have matched is exactly what has not been looked up.
     */
    internal val wildcardPlaceholders: List<String>
        get() = glob?.groupKinds.orEmpty().map { WILDCARD_PLACEHOLDER }

    /**
     * The pattern as a directory, by the same convention [ModuleResolver.Conventional] maps a
     * module with: every `:` becomes a path separator, so `":core:data"` reads as `core/data`
     * and `":feature:*"` reads as a `feature` directory with a `*` level below it.
     *
     * The [ModuleResolver] is deliberately not asked. It answers where one module lives, and a
     * pattern is not a module — there is nothing for a replaced resolver to look up until the
     * project has been listed and the pattern has become the modules it stands for.
     *
     * Escapes are carried over as written. `\*` means a literal `*` on both sides of the
     * translation, and `:` is not escapable, so splitting on it cannot cut an escape in half.
     */
    internal val conventionalDirectory: String
        get() = pattern
            .removePrefix(Glob.MODULE_SEPARATOR.toString())
            .replace(Glob.MODULE_SEPARATOR, Glob.PATH_SEPARATOR)

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
         * What `wildcards` reads as when there is no module to have matched.
         *
         * Deliberately **not** a glob metacharacter. A placeholder goes straight into whatever
         * the layout block builds out of it, and the result has to survive [Glob.compile]:
         * `"${wildcards[0]}*Preview"` — a real declaration in `sample/kmp` — would come out as
         * `**Preview` if the placeholder were `*`, and `**` inside a segment is rejected. Any
         * of `* \ { } ? [ ] ,` would fuse with its neighbours the same way, so the placeholder
         * is built from characters katachi's glob has no meaning for at all.
         *
         * It also survives the naming conversions unchanged: [me.tbsten.katachi.dsl.nameWords]
         * treats `<`, `n`, `a`, `m`, `e` and `>` alike, and neither `<` nor `>` has a case, so
         * `.pascalCase`, `.camelCase`, `.kebabCase`, `.snakeCase` and `.flatCase` all hand back
         * `<name>` (`.screamingSnakeCase` upper-cases the letters, to `<NAME>`). That is what
         * lets documentation generation find the placeholder again in a name a definition built
         * out of it.
         *
         * `<name>` rather than `<featureName>`: what a `*` captures is the name of the module
         * at that level, and nothing here knows what kind of module that is. Deriving a better
         * word from the pattern's literal segments is v0.3's question, not this one's.
         */
        internal const val WILDCARD_PLACEHOLDER: String = "<name>"

        /**
         * Translates [raw] into a pattern, filling in the leading `:` when it was left out.
         *
         * @throws KatachiGlobSyntaxException when [raw] is empty or cannot be read as a glob,
         *   or when it uses `**` anywhere but as its last segment, or more than once.
         */
        public fun compile(raw: String): ModulePattern {
            if (raw.isEmpty()) throw KatachiGlobSyntaxException(raw, GlobProblem.EmptyModulePath)
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
