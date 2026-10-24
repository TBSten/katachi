package me.tbsten.katachi.konsist.declarations.internal

/*
 * What differs between a declaration and the allowed line its name matches, worded for the
 * report's `Differs in:` line: `kind (is class, expected interface)`,
 * `modifiers (missing: data)`,
 * `modifiers (has suspend, which the line does not allow)`, `type (is Int, expected StateFlow<*>)`.
 */

/** Everything that keeps [rule] from matching [fact] in full, given that its name matches. */
internal fun differencesOf(rule: DeclarationRule, fact: DeclarationFact): List<String> = buildList {
    val kinds = rule.shapes.map { it.kind }.distinct()
    val closest = closestShapeOf(rule, fact)
    if (fact.kind !in kinds) {
        add("kind (is ${fact.kind.keyword}, expected ${kinds.joinToString(" or ") { it.keyword }})")
    }
    missingModifiersOf(closest.modifiers, fact).takeIf { it.isNotEmpty() }?.let { missing ->
        add("modifiers (missing: ${missing.joinToString(", ") { it.keyword }})")
    }
    extraModifiersOf(closest.modifiers, fact).takeIf { it.isNotEmpty() }?.let { extra ->
        add("modifiers (has ${extra.joinToString(", ") { it.keyword }}, which the line does not allow)")
    }
    missingAnnotationsOf(closest.modifiers, fact).takeIf { it.isNotEmpty() }?.let { missing ->
        add("annotations (missing: ${missing.joinToString(", ") { "@${it.written}" }})")
    }
    missingSuperclassesOf(rule, fact).takeIf { it.isNotEmpty() }?.let { missing ->
        add("superclass (missing: ${missing.joinToString(", ") { it.written }})")
    }
    val isProperty = fact.kind == DeclarationKindName.Val || fact.kind == DeclarationKindName.Var
    if (isProperty && !propertyTypeMatches(rule, fact)) {
        add("type (${isOrNotWritten(fact.propertyType)}, expected ${rule.propertyType?.written})")
    }
    val parameters = fact.parameters
    if (parameters != null && !parametersMatch(rule, fact)) {
        val actual = renderParameters(parameters)
        val expected = rule.parameters.orEmpty().joinToString(", ", "(", ")") { it.written }
        add("parameters (are $actual, expected $expected)")
    }
    if (fact.kind == DeclarationKindName.Function && !returnTypeMatches(rule, fact)) {
        add("return type (${isOrNotWritten(fact.returnType)}, expected ${rule.returnType?.written})")
    }
}

/**
 * The candidate of [rule] closest to [fact]: of its kind if there is one, then the one with the
 * fewest modifiers and annotations it lacks or carries beyond what the candidate allows.
 */
internal fun closestShapeOf(rule: DeclarationRule, fact: DeclarationFact): Shape = rule.shapes.minBy { shape ->
    val kindCost = if (shape.kind == fact.kind) 0 else UNREACHABLE
    kindCost + missingModifiersOf(shape.modifiers, fact).size + missingAnnotationsOf(shape.modifiers, fact).size +
        extraModifiersOf(shape.modifiers, fact).size
}

/** More than any count of missing modifiers, so a candidate of another kind is chosen last. */
private const val UNREACHABLE: Int = 1_000

private fun isOrNotWritten(type: TypeText?): String = if (type == null) "not written" else "is ${type.written}"
