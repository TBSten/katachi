package me.tbsten.katachi.dsl.internal

import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.KatachiGlobSyntaxException
import me.tbsten.katachi.dsl.ModulePath
import me.tbsten.katachi.dsl.ModuleResolver

/**
 * A module path as written in a layout key, wildcards and all: `":feature:*"`.
 *
 * It is the module-path half of [Glob] — the same translation to a regular expression, with
 * `:` for a separator instead of `/`, so `*` and `**` cannot come to mean two different
 * things on the two sides of the DSL. On top of the glob it adds what only a module path
 * needs: a leading `:` is optional, `":"` names the root project, `**` is restricted to
 * the last segment so that the index of every captured wildcard is the same for every match,
 * and a `capture("...")` token embedded in the key -- `":feature:${capture("feature")}"` --
 * is read and named the same way [parseCaptureSegment] reads one out of a file path.
 */
internal class ModulePattern private constructor(
    /** The pattern with its leading `:` filled in and every capture token read as `*`, e.g. `":feature:*"`. */
    val pattern: String,
    /** `null` for `":"`, which names the root project and matches nothing else. */
    private val glob: Glob?,
    /**
     * One entry per `*` in [pattern] (`**` not counted), in the order they appear: the name a
     * `capture("...")` token gave it, or `null` for a plain, unnamed `*`. Empty when the
     * pattern holds no `*` at all.
     */
    val wildcardNames: List<String?> = emptyList(),
) {
    /** Whether the pattern holds a `*` or a `**`, which is what makes a declaration optional. */
    val hasWildcard: Boolean get() = glob?.hasWildcard == true

    /** The single module named, when there is no wildcard to expand. */
    val literalPath: ModulePath?
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
    val wildcardPlaceholders: List<String>
        get() = glob?.groupKinds.orEmpty().map { WILDCARD_PLACEHOLDER }

    /**
     * [wildcardPlaceholders], with each `*` [wildcardNames] gave a name written as `<name>`
     * instead: `":feature:${capture("feature")}"` reads as `["<feature>"]`, so a message or a
     * preview built out of it says which `--arg` fills it in. A `**` -- always last, never
     * named -- keeps [WILDCARD_PLACEHOLDER].
     */
    fun wildcardPlaceholders(captureNames: List<String?>? = wildcardNames): List<String> =
        wildcardPlaceholders.mapIndexed { index, placeholder ->
            captureNames?.getOrNull(index)?.let { "<$it>" } ?: placeholder
        }

    /**
     * How many `*`s the pattern holds, `**` not counted: the number of names
     * `":...:${capture("...")}".module { }` has to give. A `**` cannot be named, because how many levels
     * it stands for is not fixed.
     */
    val singleWildcardCount: Int
        get() = glob?.groupKinds.orEmpty().count { it == GlobGroupKind.Single }

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
    val conventionalDirectory: String
        get() = pattern
            .removePrefix(Glob.MODULE_SEPARATOR.toString())
            .replace(Glob.MODULE_SEPARATOR, Glob.PATH_SEPARATOR)

    /**
     * The pattern with its `*`s replaced by [values], in order: `":feature:*"` with `home` reads
     * `":feature:home"`. A `**`, an escaped `\*` and a `*` left without a value stay as written.
     * For a message, which names the module a template run asked for.
     */
    fun filledIn(values: List<String>): String = buildString {
        var next = 0
        var index = 0
        while (index < pattern.length) {
            val character = pattern[index]
            when {
                character == '\\' && index + 1 < pattern.length -> {
                    append(character).append(pattern[index + 1])
                    index += 2
                    continue
                }
                character == '*' && pattern.getOrNull(index + 1) == '*' -> {
                    append("**")
                    index += 2
                    continue
                }
                character == '*' && next < values.size -> append(values[next++])
                else -> append(character)
            }
            index++
        }
    }

    /** Whether [module] is one of the modules this pattern names. */
    fun matches(module: ModulePath): Boolean = match(module) != null

    /**
     * What the wildcards captured when [module] matched, or `null` when it did not.
     *
     * One element per `*`, and one **per level** for the trailing `**`, so `":feature:**"`
     * against `:feature:hoge:fuga` captures `["hoge", "fuga"]` and against `:feature` itself
     * captures nothing.
     */
    fun match(module: ModulePath): List<String>? {
        val glob = glob ?: return if (module.isRoot) emptyList() else null
        return glob.match(module.value)?.wildcards
    }

    override fun equals(other: Any?): Boolean = other is ModulePattern && other.pattern == pattern

    override fun hashCode(): Int = pattern.hashCode()

    override fun toString(): String = "ModulePattern($pattern)"

    companion object {
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
         * word from the pattern's literal segments is left open.
         */
        const val WILDCARD_PLACEHOLDER: String = "<name>"

        /**
         * Translates [raw] into a pattern, filling in the leading `:` when it was left out and
         * reading every `capture("...")` token embedded in it back into [wildcardNames].
         *
         * @throws KatachiGlobSyntaxException when [raw] is empty or cannot be read as a glob,
         *   or when it uses `**` anywhere but as its last segment, or more than once.
         * @throws me.tbsten.katachi.dsl.KatachiAdjacentCaptureException when a token in [raw]
         *   touches another wildcard with no literal between them.
         */
        fun compile(raw: String, declaredAt: DeclarationSite): ModulePattern {
            if (raw.isEmpty()) throw KatachiGlobSyntaxException(raw, GlobProblem.EmptyModulePath)
            val normalized = if (raw.startsWith(Glob.MODULE_SEPARATOR)) raw else "${Glob.MODULE_SEPARATOR}$raw"
            // `":"` is the root project. It is not a glob — `Glob.compile` would read the
            // separator as the start of an empty segment.
            if (normalized == Glob.MODULE_SEPARATOR.toString()) return ModulePattern(normalized, null)
            val names = mutableListOf<String?>()
            val globSegments = normalized.substring(1).split(Glob.MODULE_SEPARATOR).map { segment ->
                if (segment == RECURSIVE_WILDCARD) {
                    // `**` cannot be named -- there is nothing to attach a token to.
                    segment
                } else {
                    val captureSegment = parseCaptureSegment(segment, declaredAt)
                    when {
                        captureSegment != null -> {
                            names += captureSegment.names
                            captureSegment.globSegment
                        }

                        segment == SINGLE_WILDCARD -> {
                            names += null
                            segment
                        }

                        else -> segment
                    }
                }
            }
            val globPattern = Glob.MODULE_SEPARATOR + globSegments.joinToString(Glob.MODULE_SEPARATOR.toString())
            val glob = Glob.compile(globPattern, Glob.MODULE_SEPARATOR)
            glob.requireAtMostOneTrailingDoubleStar()
            return ModulePattern(globPattern, glob, names.toList())
        }

        private const val SINGLE_WILDCARD: String = "*"
        private const val RECURSIVE_WILDCARD: String = "**"

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
