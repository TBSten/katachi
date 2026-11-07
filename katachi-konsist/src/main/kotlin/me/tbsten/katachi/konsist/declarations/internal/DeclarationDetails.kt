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

internal object GetterRule : DetailRule {
    override val call: String get() = "getter"

    override val rendered: String get() = ".getter()"

    override fun matches(fact: DeclarationFact): Boolean = fact.details.property?.hasGetter == true

    override fun difference(fact: DeclarationFact): String = "getter (none written, expected one)"

    override fun toString(): String = "GetterRule"
}

// modifiers: those the setter has to carry; `public` is carried by a setter that writes no visibility.
internal class SetterRule(val modifiers: Set<ModifierName>) : DetailRule {
    override val call: String get() = "setter"

    override val rendered: String get() = ".setter(${modifiers.sortedBy { it.ordinal }.joinToString(" ") { it.keyword }})"

    override fun matches(fact: DeclarationFact): Boolean {
        val property = fact.details.property ?: return false
        return property.hasSetter && modifiers.all { carries(property.setterModifiers, it) }
    }

    override fun difference(fact: DeclarationFact): String {
        val property = fact.details.property
        val actual = when {
            property == null || !property.hasSetter -> "none written"
            else -> "is " + (property.setterModifiers.sortedBy { it.ordinal }.joinToString(" ") { it.keyword } + " set").trim()
        }
        val expected = (modifiers.sortedBy { it.ordinal }.joinToString(" ") { it.keyword } + " set").trim()
        return "setter ($actual, expected $expected)"
    }

    override fun toString(): String = "SetterRule($rendered)"
}

internal class DelegateRule(val name: NameRule) : DetailRule {
    override val call: String get() = "delegatedBy"

    override val rendered: String get() = ".delegatedBy(${name.written})"

    override fun matches(fact: DeclarationFact): Boolean = fact.details.property?.delegate?.let(name::matches) == true

    override fun difference(fact: DeclarationFact): String {
        val actual = fact.details.property?.delegate?.let { "is by $it" } ?: "none"
        return "delegate ($actual, expected by ${name.written})"
    }

    override fun toString(): String = "DelegateRule($rendered)"
}

// type: null asks only for a backing field; a type asks for one written with a matching type.
internal class BackingFieldRule(val type: TypeRule?) : DetailRule {
    override val call: String get() = "backingField"

    override val rendered: String get() = ".backingField(${type?.written.orEmpty()})"

    override fun matches(fact: DeclarationFact): Boolean {
        val property = fact.details.property ?: return false
        if (!property.hasBackingField) return false
        val expected = type ?: return true
        val actual = property.backingFieldType ?: return false
        return expected.canonical.matches(actual.canonical)
    }

    override fun difference(fact: DeclarationFact): String {
        val property = fact.details.property
        val actual = when {
            property == null || !property.hasBackingField -> "none"
            else -> "field" + (property.backingFieldType?.let { ": ${it.written}" } ?: "")
        }
        return "backing field ($actual, expected field${type?.let { ": ${it.written}" } ?: ""})"
    }

    override fun toString(): String = "BackingFieldRule($rendered)"
}

// parameters: null asks nothing about them (`anyArgs()`). A parameter's `val` or `var` is compared as written: `"id: Int"` is not a property.
internal class PrimaryConstructorRule(val modifiers: Set<ModifierName>, val parameters: List<ParameterRule>?) : DetailRule {
    override val call: String get() = "primaryConstructor"

    override val rendered: String
        get() {
            val parts = modifiers.sortedBy { it.ordinal }.map { it.keyword } +
                (parameters?.map { it.written } ?: listOf("anyArgs()"))
            return ".primaryConstructor(${parts.joinToString(", ")})"
        }

    override fun matches(fact: DeclarationFact): Boolean {
        val constructor = fact.details.primaryConstructor ?: return false
        if (!modifiers.all { carries(constructor.modifiers, it) }) return false
        val expected = parameters ?: return true
        return expected.size == constructor.parameters.size &&
            expected.zip(constructor.parameters).all { (rule, written) ->
                parameterMatches(rule, written) && rule.modifiers.intersect(PROPERTY_WORDS) == written.modifiers.intersect(PROPERTY_WORDS)
            }
    }

    override fun difference(fact: DeclarationFact): String {
        val constructor = fact.details.primaryConstructor
        val actual = if (constructor == null) "none" else "is " + renderConstructor(constructor.modifiers, renderParameters(constructor.parameters))
        val expected = renderConstructor(modifiers, parameters?.joinToString(", ", "(", ")") { it.written } ?: "(...)")
        return "primary constructor ($actual, expected $expected)"
    }

    override fun toString(): String = "PrimaryConstructorRule($rendered)"
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

private val PROPERTY_WORDS: Set<String> = setOf("val", "var")

private fun carries(modifiers: Set<ModifierName>, modifier: ModifierName): Boolean =
    modifier in modifiers || (modifier == ModifierName.Public && modifiers.none { it in ModifierName.VISIBILITIES })

private fun renderConstructor(modifiers: Set<ModifierName>, parameters: String): String =
    (modifiers.sortedBy { it.ordinal }.joinToString(" ") { it.keyword } + " constructor").trim() + parameters

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
