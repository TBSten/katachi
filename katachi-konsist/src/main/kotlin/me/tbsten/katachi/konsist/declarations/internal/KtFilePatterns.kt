package me.tbsten.katachi.konsist.declarations.internal

import java.util.concurrent.ConcurrentHashMap
import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.KatachiGlobSyntaxException
import me.tbsten.katachi.dsl.NamePattern
import me.tbsten.katachi.konsist.declarations.KatachiKtFileContentPatternException
import me.tbsten.katachi.konsist.declarations.KtFileContentLineProblem
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

internal fun PatternCache.typeRule(written: String, what: KtFileContentPatternKind = KtFileContentPatternKind.Type): TypeRule {
    refusePackageWildcard(written, what)
    return TypeRule(written, compileOrThrow(written, typePatternOf(written), separator = null, what))
}

// `(() -> Unit)` and `() -> Unit` are one receiver: Kotlin needs the parentheses there, a pattern does not.
internal fun PatternCache.receiverRule(written: String): TypeRule = typeRule(withoutOuterParentheses(written.trim()), KtFileContentPatternKind.Receiver)

internal fun PatternCache.superclassRule(written: String): SuperclassRule {
    refusePackageWildcard(written, KtFileContentPatternKind.Superclass)
    val pattern = typePatternOf(written)
    return SuperclassRule(
        written = written,
        byName = '<' !in pattern,
        pattern = compileOrThrow(written, pattern, separator = null, KtFileContentPatternKind.Superclass),
    )
}

// `get:JvmName("*")`: a use-site target before a `:` and arguments in parentheses, each asked about only when written.
internal fun PatternCache.annotationRule(written: String, what: KtFileContentPatternKind = KtFileContentPatternKind.Annotation): AnnotationRule {
    val trimmed = written.trim().removePrefix("@")
    val open = trimmed.indexOf('(')
    val head = if (open < 0) trimmed else trimmed.substring(0, open)
    val target = head.substringBefore(':', missingDelimiterValue = "").trim().takeIf { ':' in head }
    val name = head.substringAfter(':').trim()
    val arguments = if (open < 0) {
        null
    } else {
        if (!trimmed.endsWith(")")) {
            throw KatachiKtFileContentPatternException(
                pattern = written,
                what = what,
                declaredAt = DeclarationSite.capture(),
                cause = null,
                problem = KtFileContentPatternRefusal.UnclosedArguments,
            )
        }
        val inside = trimmed.substring(open + 1, trimmed.length - 1).filterNot { it.isWhitespace() }
        // `()` asks for no arguments, which no glob can say: the empty text is matched by an exact rule.
        if (inside.isEmpty()) NO_ARGUMENTS else compileOrThrow(written, inside.replace("?", "\\?"), separator = null, KtFileContentPatternKind.AnnotationArguments)
    }
    val qualified = PATH_SEPARATOR in name
    val separator = if (qualified) PATH_SEPARATOR else null
    return AnnotationRule(written, qualified, compileOrThrow(written, name, separator, what), target, arguments)
}

private val PARAMETER_MODIFIERS: Set<String> = setOf("vararg", "noinline", "crossinline")

private val PROPERTY_PARAMETER_WORDS: Set<String> = setOf("val", "var")

// `vararg xs: Int = *`: modifiers in front, split at the first `:` (a name never holds one, a type may, as in
// `(result: String) -> Unit`), and the default value after the first `=` of the type (a type never holds one).
// allowProperty: a parameter of a primary constructor, which may also be written with `val` or `var`.
internal fun PatternCache.parameterRule(
    written: String,
    allowProperty: Boolean = false,
    problem: (KtFileContentLineProblem) -> Throwable,
): ParameterRule {
    var rest = written.trim()
    val modifiers = linkedSetOf<String>()
    val accepted = if (allowProperty) PARAMETER_MODIFIERS + PROPERTY_PARAMETER_WORDS else PARAMETER_MODIFIERS
    while (true) {
        val word = rest.substringBefore(' ')
        if (word !in accepted || word == rest) break
        modifiers += word
        rest = rest.substringAfter(' ').trimStart()
    }
    val colon = rest.indexOf(':')
    val name = if (colon < 0) "" else rest.substring(0, colon).trim()
    val afterColon = (if (colon < 0) rest else rest.substring(colon + 1)).trim()
    val equals = afterColon.indexOf('=')
    val type = (if (equals < 0) afterColon else afterColon.substring(0, equals)).trim()
    val default = if (equals < 0) null else afterColon.substring(equals + 1).trim()
    if (name.isEmpty()) throw problem(KtFileContentLineProblem.ParameterWithoutName)
    if (type.isEmpty()) throw problem(KtFileContentLineProblem.ParameterWithoutType)
    if (default != null && default.isEmpty()) throw problem(KtFileContentLineProblem.ParameterWithoutDefaultValue)
    return ParameterRule(
        written = written,
        name = nameRule(name, KtFileContentPatternKind.ParameterName),
        type = typeRule(type),
        modifiers = modifiers,
        defaultValue = default?.let { typeRule(it, KtFileContentPatternKind.DefaultValue) },
    )
}

internal val NO_ARGUMENTS: NamePattern = NamePattern.compile("()")

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
    UnclosedArguments(
        why = "The arguments of an annotation are written between parentheses that close at the end.",
        how = "Close the parentheses, such as \"Deprecated(*)\", or leave them out to ask nothing about the arguments.",
    ),
}
