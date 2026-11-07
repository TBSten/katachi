package me.tbsten.katachi.konsist.declarations.internal

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
    if (!receiverMatches(rule, fact)) {
        val actual = fact.receiver?.let { "is ${it.written}" } ?: "none"
        add("receiver ($actual, expected ${rule.receiver?.written})")
    }
    val isProperty = fact.kind == DeclarationKindName.Val || fact.kind == DeclarationKindName.Var
    if (isProperty && !propertyTypeMatches(rule, fact)) {
        add("type (${isOrNotWritten(fact.propertyType)}, expected ${rule.propertyType?.written})")
    }
    if (fact.kind == DeclarationKindName.TypeAlias && !propertyTypeMatches(rule, fact)) {
        add("aliased type (${isOrNotWritten(fact.propertyType)}, expected ${rule.propertyType?.written})")
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
    rule.details.filterNot { it.matches(fact) }.forEach { add(it.difference(fact)) }
}

internal fun closestShapeOf(rule: DeclarationRule, fact: DeclarationFact): Shape = rule.shapes.minBy { shape ->
    val kindCost = if (shape.kind == fact.kind) 0 else UNREACHABLE
    kindCost + missingModifiersOf(shape.modifiers, fact).size + missingAnnotationsOf(shape.modifiers, fact).size +
        extraModifiersOf(shape.modifiers, fact).size
}

private const val UNREACHABLE: Int = 1_000

private fun isOrNotWritten(type: TypeText?): String = if (type == null) "not written" else "is ${type.written}"
