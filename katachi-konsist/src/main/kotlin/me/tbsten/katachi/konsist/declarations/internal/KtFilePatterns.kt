package me.tbsten.katachi.konsist.declarations.internal

import java.util.concurrent.ConcurrentHashMap
import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.KatachiGlobSyntaxException
import me.tbsten.katachi.dsl.NamePattern
import me.tbsten.katachi.konsist.declarations.KatachiKtFileContentPatternException
import me.tbsten.katachi.konsist.declarations.KtFileContentPatternKind

/** What an import path and a package are split on. */
internal const val PATH_SEPARATOR: Char = '.'

/**
 * Every pattern one `ktFileContent { }` compiled, kept for its whole life.
 *
 * The block is evaluated once per file, and most of what it writes is the same for every file
 * (`import("kotlin.**")`), so a pattern is turned into a regular expression once rather than once
 * per file. Shared by the evaluations of one constraint, which may run on several threads.
 */
internal class PatternCache {
    private val compiled = ConcurrentHashMap<Pair<String, Char?>, NamePattern>()

    /** @throws KatachiGlobSyntaxException as [NamePattern.compile] does. Nothing is kept for a pattern that throws. */
    fun compile(pattern: String, separator: Char?): NamePattern {
        val key = pattern to separator
        return compiled[key] ?: NamePattern.compile(pattern, separator).also { compiled.putIfAbsent(key, it) }
    }

    override fun toString(): String = "PatternCache(${compiled.size} patterns)"
}

/** A declaration name, or an `except` of a declaration line. A name has no levels, so `*` matches any text. */
internal fun PatternCache.nameRule(written: String, what: KtFileContentPatternKind): NameRule =
    NameRule(written, compileOrThrow(written, written, separator = null, what))

/** An import path, a package, or an `except` of one of them. */
internal fun PatternCache.pathRule(written: String, what: KtFileContentPatternKind): NameRule =
    NameRule(written, compileOrThrow(written, written, PATH_SEPARATOR, what))

/** A property type, a parameter type or a return type, matched against the canonical form of what is written. */
internal fun PatternCache.typeRule(written: String): TypeRule {
    refusePackageWildcard(written, KtFileContentPatternKind.Type)
    return TypeRule(written, compileOrThrow(written, typePatternOf(written), separator = null, KtFileContentPatternKind.Type))
}

/** One `.superclass("...")`: by name when no type argument is written, type arguments and all otherwise. */
internal fun PatternCache.superclassRule(written: String): SuperclassRule {
    refusePackageWildcard(written, KtFileContentPatternKind.Superclass)
    val pattern = typePatternOf(written)
    return SuperclassRule(
        written = written,
        byName = '<' !in pattern,
        pattern = compileOrThrow(written, pattern, separator = null, KtFileContentPatternKind.Superclass),
    )
}

/** One name of `annotated(...)`: a simple name, or a full name when it holds a `.`. */
internal fun PatternCache.annotationRule(written: String): AnnotationRule {
    val qualified = PATH_SEPARATOR in written
    val separator = if (qualified) PATH_SEPARATOR else null
    return AnnotationRule(written, qualified, compileOrThrow(written, written, separator, KtFileContentPatternKind.Annotation))
}

/**
 * [text] with every space removed, the package part of each name taken off and a trailing comma
 * dropped: `kotlinx.coroutines.flow.StateFlow<com.example.HomeUiState>` is `StateFlow<HomeUiState>`,
 * while an outer class (`Map.Entry`) stays, being capitalised. `Map<String, Int,>` is
 * `Map<String,Int>`, the same type written another way.
 */
internal fun canonicalType(text: String): String =
    PACKAGE_QUALIFIER.replace(text.filterNot { it.isWhitespace() }, "").replace(TRAILING_COMMA, "")

/** A comma right before the `>` of type arguments or the `)` of a function type's parameters. */
private val TRAILING_COMMA = Regex(""",(?=[>)])""")

/** A run of lowercase-initial names, each followed by `.`, that a name follows: the package of a type. */
private val PACKAGE_QUALIFIER = Regex("""(?<![A-Za-z0-9_`.])(?:[a-z_][A-Za-z0-9_]*\.)+(?=[A-Za-z_`*])""")

/**
 * Refuses a type pattern that names a package and then a wildcard (`com.example.*`): types are
 * compared without their package, so it would be `*`, which matches every type.
 */
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

/** A package written in a type pattern, a wildcard right after it. */
private val PACKAGE_WILDCARD = Regex("""(?<![A-Za-z0-9_`.])(?:[a-z_][A-Za-z0-9_]*\.)+\*""")

/**
 * The pattern a type is matched with: its canonical form, with `?` made literal. In a type `?`
 * says nullable, so it is not the glob character the pattern would otherwise refuse.
 */
private fun typePatternOf(written: String): String = canonicalType(written).replace("?", "\\?")

/**
 * [pattern] compiled, or the reason it cannot be, worded for the line that wrote [written].
 *
 * Reached from the call the user wrote, so the site captured here is the line of that call.
 */
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

/** A pattern the glob reads, refused because it would not mean what it says. */
internal enum class KtFileContentPatternRefusal(val why: String, val how: String) {
    /** `com.example.*` as a type: types are compared without their package, so it is `*`. */
    PackageOfType(
        why = "Types are compared without their package, so this pattern would match every type.",
        how = "Write the type by its name without the package, such as \"*UiState\".",
    ),
}
