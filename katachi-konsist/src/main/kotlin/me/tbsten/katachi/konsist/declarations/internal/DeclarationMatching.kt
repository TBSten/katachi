package me.tbsten.katachi.konsist.declarations.internal

internal fun fullMatch(rule: DeclarationRule, fact: DeclarationFact): Boolean =
    nameMatches(rule, fact) && rule.shapes.any { shapeMatches(it, fact, exact = !rule.prohibited) } && detailsMatch(rule, fact)

internal fun nameMatches(rule: DeclarationRule, fact: DeclarationFact): Boolean =
    rule.name.matches(fact.name) && rule.except.none { it.matches(fact.name) }

internal fun shapeMatches(shape: Shape, fact: DeclarationFact, exact: Boolean): Boolean =
    fact.kind == shape.kind && modifiersSatisfied(shape.modifiers, fact) &&
        (!exact || extraModifiersOf(shape.modifiers, fact).isEmpty())

internal fun modifiersSatisfied(set: ModifierSet, fact: DeclarationFact): Boolean =
    missingModifiersOf(set, fact).isEmpty() && missingAnnotationsOf(set, fact).isEmpty()

// `public` counts as carried by a declaration that writes no visibility at all, as in Kotlin.
internal fun missingModifiersOf(set: ModifierSet, fact: DeclarationFact): List<ModifierName> =
    set.modifiers.filterNot { hasModifier(fact, it) }.sortedBy { it.ordinal }

// `public` is never extra (no visibility reads as public), nor is a modifier that takes the place of a missing one (`private` where `public` is asked).
internal fun extraModifiersOf(set: ModifierSet, fact: DeclarationFact): List<ModifierName> {
    val allowed = set.modifiers + set.alsoAllowed
    val missing = missingModifiersOf(set, fact).toSet()
    return fact.modifiers
        .filter { it != ModifierName.Public && it !in allowed && excludedBy(it, missing) == null }
        .sortedBy { it.ordinal }
}

internal fun missingAnnotationsOf(set: ModifierSet, fact: DeclarationFact): List<AnnotationRule> =
    set.annotations.filterNot { rule -> fact.annotations.any { annotationMatches(rule, it) } }

private fun hasModifier(fact: DeclarationFact, modifier: ModifierName): Boolean =
    modifier in fact.modifiers ||
        (modifier == ModifierName.Public && fact.modifiers.none { it in ModifierName.VISIBILITIES })

private fun annotationMatches(rule: AnnotationRule, annotation: AnnotationFact): Boolean =
    if (rule.qualified) {
        annotation.qualifiedCandidates.any { rule.pattern.matches(it) }
    } else {
        rule.pattern.matches(annotation.simpleName)
    }

private fun detailsMatch(rule: DeclarationRule, fact: DeclarationFact): Boolean =
    missingSuperclassesOf(rule, fact).isEmpty() &&
        propertyTypeMatches(rule, fact) &&
        parametersMatch(rule, fact) &&
        returnTypeMatches(rule, fact)

internal fun missingSuperclassesOf(rule: DeclarationRule, fact: DeclarationFact): List<SuperclassRule> =
    rule.superclasses.filterNot { superclass ->
        fact.supertypes.any { supertype ->
            superclass.pattern.matches(if (superclass.byName) supertype.withoutArguments else supertype.canonical)
        }
    }

internal fun propertyTypeMatches(rule: DeclarationRule, fact: DeclarationFact): Boolean {
    val expected = rule.propertyType ?: return true
    val actual = fact.propertyType ?: return false
    return expected.canonical.matches(actual.canonical)
}

internal fun parametersMatch(rule: DeclarationRule, fact: DeclarationFact): Boolean {
    val expected = rule.parameters ?: return true
    val actual = fact.parameters ?: return false
    return expected.size == actual.size && expected.zip(actual).all { (parameter, written) -> parameterMatches(parameter, written) }
}

private fun parameterMatches(rule: ParameterRule, fact: ParameterFact): Boolean =
    rule.name.matches(fact.name) && rule.type.canonical.matches(fact.type.canonical)

internal fun returnTypeMatches(rule: DeclarationRule, fact: DeclarationFact): Boolean {
    val expected = rule.returnType ?: return true
    val actual = fact.returnType ?: return false
    return expected.canonical.matches(actual.canonical)
}

internal fun anythingElseMatches(rule: AnythingElseRule, fact: DeclarationFact): Boolean =
    rule.alternatives.any { modifiersSatisfied(it, fact) }
