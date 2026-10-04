package me.tbsten.katachi.konsist.declarations.internal

/*
 * What differs between a declaration and the allowed line its name matches, worded for the
 * report's `Differs in:` line: `kind (is class, expected interface)`,
 * `modifiers (missing: data)`, `type (is Int, expected StateFlow<*>)`.
 */

/** Everything that keeps [rule] from matching [fact] in full, given that its name matches. */
internal fun differencesOf(rule: DeclarationRule, fact: DeclarationFact): List<String> = buildList {
    val kinds = rule.shapes.map { it.kind }.distinct()
    // The candidate closest to the declaration: of its kind if there is one, then the one asking
    // for the fewest modifiers and annotations it lacks.
    val closest = rule.shapes.minBy { shape ->
        val kindCost = if (shape.kind == fact.kind) 0 else UNREACHABLE
        kindCost + missingModifiersOf(shape.modifiers, fact).size + missingAnnotationsOf(shape.modifiers, fact).size
    }
    if (fact.kind !in kinds) {
        add("kind (is ${fact.kind.keyword}, expected ${kinds.joinToString(" or ") { it.keyword }})")
    }
    missingModifiersOf(closest.modifiers, fact).takeIf { it.isNotEmpty() }?.let { missing ->
        add("modifiers (missing: ${missing.joinToString(", ") { it.keyword }})")
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
    val parameterTypes = fact.parameterTypes
    if (parameterTypes != null && !parametersMatch(rule, fact)) {
        val actual = parameterTypes.joinToString(", ", "(", ")") { it.written }
        val expected = rule.parameters.orEmpty().joinToString(", ", "(", ")") { it.written }
        add("parameters (are $actual, expected $expected)")
    }
    if (fact.kind == DeclarationKindName.Function && !returnTypeMatches(rule, fact)) {
        add("return type (${isOrNotWritten(fact.returnType)}, expected ${rule.returnType?.written})")
    }
}

/** More than any count of missing modifiers, so a candidate of another kind is chosen last. */
private const val UNREACHABLE: Int = 1_000

private fun isOrNotWritten(type: TypeText?): String = if (type == null) "not written" else "is ${type.written}"
