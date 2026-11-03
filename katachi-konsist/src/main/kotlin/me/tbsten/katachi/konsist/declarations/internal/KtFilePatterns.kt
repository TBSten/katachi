package me.tbsten.katachi.konsist.declarations.internal

import java.util.concurrent.ConcurrentHashMap
import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.KatachiGlobSyntaxException
import me.tbsten.katachi.dsl.NamePattern
import me.tbsten.katachi.konsist.declarations.KatachiKtFileContentPatternException
import me.tbsten.katachi.konsist.declarations.KtFileContentPatternKind

internal const val PATH_SEPARATOR: Char = '.'

// Shared by the evaluations of one constraint, which may run on several threads.
internal class PatternCache {
    private val compiled = ConcurrentHashMap<Pair<String, Char?>, NamePattern>()

    // Nothing is kept for a pattern that throws.
    fun compile(pattern: String, separator: Char?): NamePattern {
        val key = pattern to separator
        return compiled[key] ?: NamePattern.compile(pattern, separator).also { compiled.putIfAbsent(key, it) }
    }

    override fun toString(): String = "PatternCache(${compiled.size} patterns)"
}

internal fun PatternCache.nameRule(written: String, what: KtFileContentPatternKind): NameRule =
    NameRule(written, compileOrThrow(written, written, separator = null, what))

internal fun PatternCache.pathRule(written: String, what: KtFileContentPatternKind): NameRule =
    NameRule(written, compileOrThrow(written, written, PATH_SEPARATOR, what))

internal fun PatternCache.typeRule(written: String): TypeRule {
    refusePackageWildcard(written, KtFileContentPatternKind.Type)
    return TypeRule(written, compileOrThrow(written, typePatternOf(written), separator = null, KtFileContentPatternKind.Type))
}

internal fun PatternCache.superclassRule(written: String): SuperclassRule {
    refusePackageWildcard(written, KtFileContentPatternKind.Superclass)
    val pattern = typePatternOf(written)
    return SuperclassRule(
        written = written,
        byName = '<' !in pattern,
        pattern = compileOrThrow(written, pattern, separator = null, KtFileContentPatternKind.Superclass),
    )
}

internal fun PatternCache.annotationRule(written: String): AnnotationRule {
    val qualified = PATH_SEPARATOR in written
    val separator = if (qualified) PATH_SEPARATOR else null
    return AnnotationRule(written, qualified, compileOrThrow(written, written, separator, KtFileContentPatternKind.Annotation))
}

internal fun canonicalType(text: String): String =
    PACKAGE_QUALIFIER.replace(text.filterNot { it.isWhitespace() }, "").replace(TRAILING_COMMA, "")

private val TRAILING_COMMA = Regex(""",(?=[>)])""")

private val PACKAGE_QUALIFIER = Regex("""(?<![A-Za-z0-9_`.])(?:[a-z_][A-Za-z0-9_]*\.)+(?=[A-Za-z_`*])""")

// Types are compared without their package, so `com.example.*` would act as `*`, which matches every type.
private fun refusePackageWildcard(written: String, what: KtFileContentPatternKind) {
    if (!PACKAGE_WILDCARD.containsMatchIn(written.filterNot { it.isWhitespace() })) return
    throw KatachiKtFileContentPatternException(
        pattern = written,
        what = what,
        declaredAt = DeclarationSite.capture(),
        cause = null,
        problem = KtFileContentPatternRefusal.PackageOfType,
    )
}

private val PACKAGE_WILDCARD = Regex("""(?<![A-Za-z0-9_`.])(?:[a-z_][A-Za-z0-9_]*\.)+\*""")

// In a type `?` says nullable, so it is not the glob character the pattern would otherwise refuse.
private fun typePatternOf(written: String): String = canonicalType(written).replace("?", "\\?")

// Reached from the call the user wrote, so the site captured here is the line of that call.
private fun PatternCache.compileOrThrow(
    written: String,
    pattern: String,
    separator: Char?,
    what: KtFileContentPatternKind,
): NamePattern =
    try {
        compile(pattern, separator)
    } catch (cause: KatachiGlobSyntaxException) {
        throw KatachiKtFileContentPatternException(
            pattern = written,
            what = what,
            declaredAt = DeclarationSite.capture(),
            cause = cause,
        )
    }

internal enum class KtFileContentPatternRefusal(val why: String, val how: String) {
    PackageOfType(
        why = "Types are compared without their package, so this pattern would match every type.",
        how = "Write the type by its name without the package, such as \"*UiState\".",
    ),
}
