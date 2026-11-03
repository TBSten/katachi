package me.tbsten.katachi.dsl.internal

import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.KatachiCaptureTransformInModuleKeyException
import me.tbsten.katachi.dsl.KatachiGlobSyntaxException
import me.tbsten.katachi.dsl.ModulePath
import me.tbsten.katachi.dsl.ModuleResolver

// Module-path half of Glob (`:` separator) so `*` and `**` mean the same on both sides; `**` only in the last
// segment.
internal class ModulePattern private constructor(
    val pattern: String,
    private val glob: Glob?,
    // A null entry is a plain unnamed `*`; `**` is not counted.
    val wildcardNames: List<String?> = emptyList(),
) {
    val hasWildcard: Boolean get() = glob?.hasWildcard == true

    val literalPath: ModulePath?
        get() = if (hasWildcard) null else ModulePath.of(GlobLexer.unescapeLiteral(pattern))

    // A `**` gives one entry: how many levels it would match is unknown without a module.
    val wildcardPlaceholders: List<String>
        get() = glob?.groupKinds.orEmpty().map { WILDCARD_PLACEHOLDER }

    fun wildcardPlaceholders(captureNames: List<String?>? = wildcardNames): List<String> =
        wildcardPlaceholders.mapIndexed { index, placeholder ->
            captureNames?.getOrNull(index)?.let { "<$it>" } ?: placeholder
        }

    // A `**` cannot be named: how many levels it stands for is not fixed.
    val singleWildcardCount: Int
        get() = glob?.groupKinds.orEmpty().count { it == GlobGroupKind.Single }

    // ModuleResolver is deliberately not asked: a pattern is not a module, so there is nothing for a replaced
    // resolver to look up yet.
    val conventionalDirectory: String
        get() = pattern
            .removePrefix(Glob.MODULE_SEPARATOR.toString())
            .replace(Glob.MODULE_SEPARATOR, Glob.PATH_SEPARATOR)

    fun filledIn(values: List<String>): String {
        var next = 0
        return pattern.split(Glob.MODULE_SEPARATOR).joinToString(Glob.MODULE_SEPARATOR.toString()) { segment ->
            // A `**` is a whole level of its own and is never given a value.
            if (segment == RECURSIVE_WILDCARD) {
                segment
            } else {
                GlobLexer.replaceCapturingStars(segment) { values.getOrNull(next++) }
            }
        }
    }

    fun matches(module: ModulePath): Boolean = match(module) != null

    // `**` captures one element per level, and none when it matched nothing.
    fun match(module: ModulePath): List<String>? {
        val glob = glob ?: return if (module.isRoot) emptyList() else null
        return glob.match(module.value)?.wildcards
    }

    override fun equals(other: Any?): Boolean = other is ModulePattern && other.pattern == pattern

    override fun hashCode(): Int = pattern.hashCode()

    override fun toString(): String = "ModulePattern($pattern)"

    companion object {
        // Must not be a glob metacharacter: it is spliced into layout keys that go through Glob.compile (`*` would
        // make `**Preview`).
        const val WILDCARD_PLACEHOLDER: String = "<name>"

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
                            captureSegment.captures.firstOrNull { it.transform != null }?.let { derived ->
                                throw KatachiCaptureTransformInModuleKeyException(
                                    name = derived.name,
                                    transform = derived.transform?.spelling ?: "",
                                    moduleKey = displayCaptureTokens(raw),
                                    declaredAt = declaredAt,
                                )
                            }
                            names += captureSegment.names
                            captureSegment.globSegment
                        }

                        // TODO: a `*` mixed with other characters (`feature-*`) is not counted in `names`, so it
                        //  is never matched to a `capture(...)` index; independent of the glob syntax.
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
    }
}
