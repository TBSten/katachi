package me.tbsten.katachi.konsist.declarations.internal

/*
 * Whether one declaration line matches one declaration, piece by piece. The judging asks
 * `fullMatch` and `nameMatches`; `DeclarationDifferences.kt` asks the pieces to say what differs.
 */

/** The line matches the declaration in full: name, kind with modifiers and annotations, and the details. */
internal fun fullMatch(rule: DeclarationRule, fact: DeclarationFact): Boolean =
    nameMatches(rule, fact) && rule.shapes.any { shapeMatches(it, fact) } && detailsMatch(rule, fact)

/** The line's name pattern matches the declaration's name, and none of its `except` does. */
internal fun nameMatches(rule: DeclarationRule, fact: DeclarationFact): Boolean =
    rule.name.matches(fact.name) && rule.except.none { it.matches(fact.name) }

/** The declaration is of [shape]'s kind and carries every modifier and annotation it asks for. */
internal fun shapeMatches(shape: Shape, fact: DeclarationFact): Boolean =
    fact.kind == shape.kind && modifiersSatisfied(shape.modifiers, fact)

/** The declaration carries every modifier and annotation of [set]. An empty set is satisfied by anything. */
internal fun modifiersSatisfied(set: ModifierSet, fact: DeclarationFact): Boolean =
    missingModifiersOf(set, fact).isEmpty() && missingAnnotationsOf(set, fact).isEmpty()

/**
 * The modifiers of [set] the declaration does not carry, in Kotlin's order. `public` is carried
 * by a declaration that writes it and by one that writes no visibility at all, as in Kotlin.
 */
internal fun missingModifiersOf(set: ModifierSet, fact: DeclarationFact): List<ModifierName> =
    set.modifiers.filterNot { hasModifier(fact, it) }.sortedBy { it.ordinal }

/** The annotations of [set] the declaration does not carry, in the order written. */
internal fun missingAnnotationsOf(set: ModifierSet, fact: DeclarationFact): List<AnnotationRule> =
    set.annotations.filterNot { rule -> fact.annotations.any { annotationMatches(rule, it) } }

private fun hasModifier(fact: DeclarationFact, modifier: ModifierName): Boolean =
    modifier in fact.modifiers ||
        (modifier == ModifierName.Public && fact.modifiers.none { it in ModifierName.VISIBILITIES })

/** A simple name is compared with the simple name; a full name with every full name the annotation may stand for. */
private fun annotationMatches(rule: AnnotationRule, annotation: AnnotationFact): Boolean =
    if (rule.qualified) {
        annotation.qualifiedCandidates.any { rule.pattern.matches(it) }
    } else {
        rule.pattern.matches(annotation.simpleName)
    }

/** Supertypes, property type, parameters and return type, each only when the line asks about it. */
private fun detailsMatch(rule: DeclarationRule, fact: DeclarationFact): Boolean =
    missingSuperclassesOf(rule, fact).isEmpty() &&
        propertyTypeMatches(rule, fact) &&
        parametersMatch(rule, fact) &&
        returnTypeMatches(rule, fact)

/** The `.superclass(...)` of the line that none of the declaration's supertypes matches. */
internal fun missingSuperclassesOf(rule: DeclarationRule, fact: DeclarationFact): List<SuperclassRule> =
    rule.superclasses.filterNot { superclass ->
        fact.supertypes.any { supertype ->
            superclass.pattern.matches(if (superclass.byName) supertype.withoutArguments else supertype.canonical)
        }
    }

/** No type asked for, or a written type that matches it. An inferred type matches nothing. */
internal fun propertyTypeMatches(rule: DeclarationRule, fact: DeclarationFact): Boolean {
    val expected = rule.propertyType ?: return true
    val actual = fact.propertyType ?: return false
    return expected.canonical.matches(actual.canonical)
}

/** No parameters asked for, or as many parameter types as asked, each matching in order. */
internal fun parametersMatch(rule: DeclarationRule, fact: DeclarationFact): Boolean {
    val expected = rule.parameters ?: return true
    val actual = fact.parameterTypes ?: return false
    return expected.size == actual.size && expected.zip(actual).all { (type, written) -> type.canonical.matches(written.canonical) }
}

/** No return type asked for, or one (written, or `Unit` for a block body) that matches it. */
internal fun returnTypeMatches(rule: DeclarationRule, fact: DeclarationFact): Boolean {
    val expected = rule.returnType ?: return true
    val actual = fact.returnType ?: return false
    return expected.canonical.matches(actual.canonical)
}

/** The declaration matches one of the alternatives of an `allow(..., anythingElse)`. */
internal fun anythingElseMatches(rule: AnythingElseRule, fact: DeclarationFact): Boolean =
    rule.alternatives.any { modifiersSatisfied(it, fact) }
