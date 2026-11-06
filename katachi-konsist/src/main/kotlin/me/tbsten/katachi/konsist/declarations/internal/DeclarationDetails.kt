package me.tbsten.katachi.konsist.declarations.internal

import me.tbsten.katachi.konsist.declarations.KtFileContentLineProblem
import me.tbsten.katachi.konsist.declarations.KtFileContentPatternKind

// A detail is a call chained after a line (`.context(...)`, `.kdoc()`, ...). It is asked about only when written, and each is written once a line.

internal sealed interface DetailRule {
    // The name of the call, which `DetailWrittenTwice` names.
    val call: String

    // As written after the line, without quotes: `.context(logger: Logger)`.
    val rendered: String

    fun matches(fact: DeclarationFact): Boolean

    fun difference(fact: DeclarationFact): String
}

internal class ContextRule(val parameters: List<ParameterRule>) : DetailRule {
    override val call: String get() = "context"

    override val rendered: String get() = ".context(${parameters.joinToString(", ") { it.written }})"

    override fun matches(fact: DeclarationFact): Boolean {
        val actual = fact.details.contextParameters
        return actual.size == parameters.size && parameters.zip(actual).all { (rule, written) -> parameterMatches(rule, written) }
    }

    override fun difference(fact: DeclarationFact): String =
        "context parameters (are ${renderParameters(fact.details.contextParameters)}, expected (${parameters.joinToString(", ") { it.written }}))"

    override fun toString(): String = "ContextRule($rendered)"
}

// modifiers: of `reified`, `in` and `out`, those the type parameter has to carry. bound: null asks nothing.
internal class TypeParameterRule(val written: String, val name: NameRule, val modifiers: Set<String>, val bound: TypeRule?) {
    fun matches(fact: TypeParameterFact): Boolean =
        name.matches(fact.name) && fact.modifiers.containsAll(modifiers) &&
            (bound == null || fact.bounds.any { bound.canonical.matches(it.canonical) })

    override fun toString(): String = "TypeParameterRule($written)"
}

internal class TypeParametersRule(val parameters: List<TypeParameterRule>) : DetailRule {
    override val call: String get() = "typeParameters"

    override val rendered: String get() = ".typeParameters(${parameters.joinToString(", ") { it.written }})"

    override fun matches(fact: DeclarationFact): Boolean {
        val actual = fact.details.typeParameters
        return actual.size == parameters.size && parameters.zip(actual).all { (rule, written) -> rule.matches(written) }
    }

    override fun difference(fact: DeclarationFact): String {
        val actual = fact.details.typeParameters.joinToString(", ", "<", ">") { renderTypeParameter(it) }
        return "type parameters (are ${if (fact.details.typeParameters.isEmpty()) "none" else actual}, " +
            "expected ${if (parameters.isEmpty()) "none" else parameters.joinToString(", ", "<", ">") { it.written }})"
    }

    override fun toString(): String = "TypeParametersRule($rendered)"
}

internal object KDocRule : DetailRule {
    override val call: String get() = "kdoc"

    override val rendered: String get() = ".kdoc()"

    override fun matches(fact: DeclarationFact): Boolean = fact.details.hasKDoc

    override fun difference(fact: DeclarationFact): String = "KDoc (none, expected one)"

    override fun toString(): String = "KDocRule"
}

internal class BodyRule(val kind: BodyKind) : DetailRule {
    override val call: String get() = "body"

    override val rendered: String get() = ".${kind.call}()"

    override fun matches(fact: DeclarationFact): Boolean = fact.details.body == kind

    override fun difference(fact: DeclarationFact): String =
        "body (${fact.details.body?.let { "is ${it.label}" } ?: "none"}, expected ${kind.label})"

    override fun toString(): String = "BodyRule($rendered)"
}

internal fun renderTypeParameter(fact: TypeParameterFact): String =
    (fact.modifiers.toList() + fact.name).joinToString(" ") + if (fact.bounds.isEmpty()) "" else " : " + fact.bounds.joinToString(" & ") { it.written }

private val TYPE_PARAMETER_MODIFIERS: Set<String> = setOf("reified", "in", "out")

// `reified T : Any`: modifiers in front, the name, then a bound after `:`.
internal fun PatternCache.typeParameterRule(written: String, problem: (KtFileContentLineProblem) -> Throwable): TypeParameterRule {
    var rest = written.trim()
    val modifiers = linkedSetOf<String>()
    while (true) {
        val word = rest.substringBefore(' ')
        if (word !in TYPE_PARAMETER_MODIFIERS || word == rest) break
        modifiers += word
        rest = rest.substringAfter(' ').trimStart()
    }
    val name = rest.substringBefore(':').trim()
    val bound = if (':' in rest) rest.substringAfter(':').trim() else null
    if (name.isEmpty()) throw problem(KtFileContentLineProblem.ParameterWithoutName)
    if (bound != null && bound.isEmpty()) throw problem(KtFileContentLineProblem.ParameterWithoutType)
    return TypeParameterRule(
        written = written.trim(),
        name = nameRule(name, KtFileContentPatternKind.TypeParameter),
        modifiers = modifiers,
        bound = bound?.let { typeRule(it, KtFileContentPatternKind.TypeParameter) },
    )
}
