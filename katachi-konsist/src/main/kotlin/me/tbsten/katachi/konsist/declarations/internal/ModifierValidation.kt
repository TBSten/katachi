package me.tbsten.katachi.konsist.declarations.internal

import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.konsist.declarations.KatachiKtFileContentModifierException
import me.tbsten.katachi.konsist.declarations.KtFileContentModifierProblem
import me.tbsten.katachi.konsist.declarations.internal.DeclarationKindName.Class
import me.tbsten.katachi.konsist.declarations.internal.DeclarationKindName.Function
import me.tbsten.katachi.konsist.declarations.internal.DeclarationKindName.Interface
import me.tbsten.katachi.konsist.declarations.internal.DeclarationKindName.Object
import me.tbsten.katachi.konsist.declarations.internal.DeclarationKindName.TypeAlias
import me.tbsten.katachi.konsist.declarations.internal.DeclarationKindName.Val
import me.tbsten.katachi.konsist.declarations.internal.DeclarationKindName.Var

internal val EXCLUSIVE_GROUPS: List<Set<ModifierName>> = listOf(
    ModifierName.VISIBILITIES,
    setOf(ModifierName.Data, ModifierName.Sealed, ModifierName.Enum, ModifierName.Value, ModifierName.Annotation),
    setOf(ModifierName.Abstract, ModifierName.Open, ModifierName.Final),
    setOf(ModifierName.Expect, ModifierName.Actual),
)

private val CLASS_BODY_ONLY: List<ModifierName> =
    listOf(ModifierName.Protected, ModifierName.Inner, ModifierName.Companion, ModifierName.Override)

private val MEMBER_ONLY_ON_CALLABLES: List<ModifierName> = listOf(ModifierName.Abstract, ModifierName.Open)

private val CALLABLE_KINDS: Set<DeclarationKindName> = setOf(Function, Val, Var)

private val ALL_KINDS: Set<DeclarationKindName> = DeclarationKindName.entries.toSet()

internal fun ModifierName.applicableKinds(): Set<DeclarationKindName> = when (this) {
    ModifierName.Public, ModifierName.Internal, ModifierName.Private,
    ModifierName.Protected, ModifierName.Expect, ModifierName.Actual,
    -> ALL_KINDS

    ModifierName.Data -> setOf(Class, Object)
    ModifierName.Sealed -> setOf(Class, Interface)
    ModifierName.Enum, ModifierName.Value, ModifierName.Annotation, ModifierName.Inner -> setOf(Class)
    ModifierName.Abstract, ModifierName.Open, ModifierName.Final -> setOf(Class, Function, Val, Var)
    ModifierName.Fun -> setOf(Interface)
    ModifierName.Companion -> setOf(Object)
    ModifierName.Override -> setOf(Function, Val, Var)
    ModifierName.Operator, ModifierName.Infix, ModifierName.Suspend, ModifierName.Tailrec -> setOf(Function)
    // An inline property (`inline val Int.twice get() = ...`) is Kotlin too.
    ModifierName.Inline -> setOf(Function, Val, Var)
    ModifierName.External -> setOf(Class, Interface, Object, Function, Val, Var)

    ModifierName.Const -> setOf(Val)
    ModifierName.Lateinit -> setOf(Var)
}

// Refused only when no candidate is left: `allow(public or internal, internal, ...)` still matches an internal declaration.
internal fun validatedShapes(
    raw: List<Shape>,
    placement: Placement,
    line: String,
    declaredAt: DeclarationSite,
): List<Shape> {
    val sets = withoutConflicts(raw.map { it.modifiers }.distinct(), line, declaredAt)
    for (set in sets) checkPlacement(set, placement, line, declaredAt)
    val shapes = raw.distinct().filter { it.modifiers in sets }
    if (placement == Placement.File) {
        for (shape in shapes) {
            if (shape.kind !in CALLABLE_KINDS) continue
            val memberOnly = MEMBER_ONLY_ON_CALLABLES.firstOrNull { it in shape.modifiers.modifiers } ?: continue
            throw KatachiKtFileContentModifierException(
                line = line,
                problem = KtFileContentModifierProblem.OnlyInClassBody,
                modifiers = listOf(memberOnly.keyword),
                kind = shape.kind.keyword,
                declaredAt = declaredAt,
            )
        }
    }
    val kept = shapes.filter { shape -> shape.modifiers.modifiers.all { shape.kind in it.applicableKinds() } }
    if (kept.isNotEmpty()) return kept.also { checkOptional(it, placement, line, declaredAt) }
    val first = shapes.first()
    val offending = first.modifiers.modifiers.sortedBy { it.ordinal }.first { first.kind !in it.applicableKinds() }
    throw KatachiKtFileContentModifierException(
        line = line,
        problem = KtFileContentModifierProblem.NotApplicable,
        modifiers = listOf(offending.keyword),
        kind = first.kind.keyword,
        declaredAt = declaredAt,
    )
}

internal fun validatedAnythingElse(
    alternatives: List<ModifierSet>,
    placement: Placement,
    line: String,
    declaredAt: DeclarationSite,
): List<ModifierSet> {
    val sets = withoutConflicts(alternatives.distinct(), line, declaredAt)
    for (set in sets) checkPlacement(set, placement, line, declaredAt)
    return sets
}

// A value with one modifier that fits is kept, as `(internal or private).optional()` is next to `internal`.
private fun checkOptional(shapes: List<Shape>, placement: Placement, line: String, declaredAt: DeclarationSite) {
    for (group in shapes.first().modifiers.optional) {
        val sorted = group.sortedBy { it.ordinal }
        if (placement == Placement.File) {
            val memberOnly = CLASS_BODY_ONLY.firstOrNull { it in group }
                ?: shapes.firstOrNull { it.kind in CALLABLE_KINDS }?.let { MEMBER_ONLY_ON_CALLABLES.firstOrNull { it in group } }
            if (memberOnly != null) {
                val callable = shapes.firstOrNull { it.kind in CALLABLE_KINDS }
                throw KatachiKtFileContentModifierException(
                    line = line,
                    problem = KtFileContentModifierProblem.OnlyInClassBody,
                    modifiers = listOf(memberOnly.keyword),
                    kind = if (memberOnly in CLASS_BODY_ONLY) null else callable?.kind?.keyword,
                    declaredAt = declaredAt,
                )
            }
        }
        if (sorted.none { modifier -> shapes.any { it.kind in modifier.applicableKinds() } }) {
            throw KatachiKtFileContentModifierException(
                line = line,
                problem = KtFileContentModifierProblem.NotApplicable,
                modifiers = listOf(sorted.first().keyword),
                kind = shapes.first().kind.keyword,
                declaredAt = declaredAt,
            )
        }
        val conflicts = shapes.map { shape -> sorted.map { excludedBy(it, shape.modifiers.modifiers) } }
        if (conflicts.all { perShape -> perShape.all { it != null } }) {
            val excluding = conflicts.first().first() ?: continue
            throw KatachiKtFileContentModifierException(
                line = line,
                problem = KtFileContentModifierProblem.Conflicting,
                modifiers = listOf(excluding, sorted.first()).sortedBy { it.ordinal }.map { it.keyword },
                kind = null,
                declaredAt = declaredAt,
            )
        }
    }
}

internal fun excludedBy(modifier: ModifierName, required: Set<ModifierName>): ModifierName? = EXCLUSIVE_GROUPS
    .filter { modifier in it }
    .firstNotNullOfOrNull { group -> required.firstOrNull { it != modifier && it in group } }

private fun conflictIn(set: ModifierSet): List<ModifierName>? = EXCLUSIVE_GROUPS
    .map { group -> set.modifiers.filter { it in group }.sortedBy { it.ordinal } }
    .firstOrNull { it.size >= 2 }

private fun withoutConflicts(sets: List<ModifierSet>, line: String, declaredAt: DeclarationSite): List<ModifierSet> {
    val kept = sets.filter { conflictIn(it) == null }
    if (kept.isNotEmpty() || sets.isEmpty()) return kept
    val together = sets.firstNotNullOf { conflictIn(it) }
    throw KatachiKtFileContentModifierException(
        line = line,
        problem = KtFileContentModifierProblem.Conflicting,
        modifiers = together.map { it.keyword },
        kind = null,
        declaredAt = declaredAt,
    )
}

private fun checkPlacement(set: ModifierSet, placement: Placement, line: String, declaredAt: DeclarationSite) {
    if (placement != Placement.File) return
    val memberOnly = CLASS_BODY_ONLY.firstOrNull { it in set.modifiers } ?: return
    throw KatachiKtFileContentModifierException(
        line = line,
        problem = KtFileContentModifierProblem.OnlyInClassBody,
        modifiers = listOf(memberOnly.keyword),
        kind = null,
        declaredAt = declaredAt,
    )
}
