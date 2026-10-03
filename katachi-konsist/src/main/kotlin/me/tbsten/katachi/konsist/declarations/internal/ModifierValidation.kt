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

/*
 * Which modifiers a line may combine, and on which kinds. A line whose
 * combination no declaration can have is refused while the definition is read, rather than
 * left to match nothing.
 */

/** Groups of modifiers of which one declaration carries at most one. */
private val EXCLUSIVE_GROUPS: List<Set<ModifierName>> = listOf(
    ModifierName.VISIBILITIES,
    setOf(ModifierName.Data, ModifierName.Sealed, ModifierName.Enum, ModifierName.Value, ModifierName.Annotation),
    setOf(ModifierName.Abstract, ModifierName.Open),
    setOf(ModifierName.Expect, ModifierName.Actual),
)

/** Modifiers Kotlin accepts only on a member, in the order a message names the first of them. */
private val CLASS_BODY_ONLY: List<ModifierName> =
    listOf(ModifierName.Protected, ModifierName.Inner, ModifierName.Companion, ModifierName.Override)

/** Modifiers a fun, val or var can carry only as a member. */
private val MEMBER_ONLY_ON_CALLABLES: List<ModifierName> = listOf(ModifierName.Abstract, ModifierName.Open)

private val CALLABLE_KINDS: Set<DeclarationKindName> = setOf(Function, Val, Var)

private val ALL_KINDS: Set<DeclarationKindName> = DeclarationKindName.entries.toSet()

/** The kinds [this] can be put on. Whether it may be written directly in the file is checked separately. */
internal fun ModifierName.applicableKinds(): Set<DeclarationKindName> = when (this) {
    ModifierName.Public, ModifierName.Internal, ModifierName.Private,
    ModifierName.Protected, ModifierName.Expect, ModifierName.Actual,
    -> ALL_KINDS

    ModifierName.Data -> setOf(Class, Object)
    ModifierName.Sealed -> setOf(Class, Interface)
    ModifierName.Enum, ModifierName.Value, ModifierName.Annotation, ModifierName.Inner -> setOf(Class)
    ModifierName.Abstract, ModifierName.Open -> setOf(Class, Function, Val, Var)
    ModifierName.Fun -> setOf(Interface)
    ModifierName.Companion -> setOf(Object)
    ModifierName.Override -> setOf(Function, Val, Var)
    ModifierName.Operator, ModifierName.Infix, ModifierName.Suspend,
    ModifierName.Inline, ModifierName.Tailrec, ModifierName.External,
    -> setOf(Function)

    ModifierName.Const -> setOf(Val)
    ModifierName.Lateinit -> setOf(Var)
}

/**
 * [raw] with every candidate no declaration can have removed, in order.
 *
 * @throws KatachiKtFileContentModifierException when a set holds two modifiers that exclude each
 *   other, uses a member-only modifier directly in the file, or when no candidate is left.
 */
internal fun validatedShapes(
    raw: List<Shape>,
    placement: Placement,
    line: String,
    declaredAt: DeclarationSite,
): List<Shape> {
    val shapes = raw.distinct()
    for (set in shapes.map { it.modifiers }.distinct()) {
        checkCombinable(set, placement, line, declaredAt)
    }
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
    if (kept.isNotEmpty()) return kept
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

/**
 * Checks the alternatives of an `anythingElse()`: no kind is named, so only whether each set can
 * be on one declaration, and whether it may be written where it is.
 *
 * @throws KatachiKtFileContentModifierException as [validatedShapes] does for its first two checks.
 */
internal fun validateAnythingElse(
    alternatives: List<ModifierSet>,
    placement: Placement,
    line: String,
    declaredAt: DeclarationSite,
) {
    for (set in alternatives.distinct()) checkCombinable(set, placement, line, declaredAt)
}

/** Whether a kind is one a `typealias` line names, which only the file can hold. */
internal fun List<Shape>.namesTypeAlias(): Boolean = any { it.kind == TypeAlias }

private fun checkCombinable(set: ModifierSet, placement: Placement, line: String, declaredAt: DeclarationSite) {
    for (group in EXCLUSIVE_GROUPS) {
        val together = set.modifiers.filter { it in group }.sortedBy { it.ordinal }
        if (together.size < 2) continue
        throw KatachiKtFileContentModifierException(
            line = line,
            problem = KtFileContentModifierProblem.Conflicting,
            modifiers = together.map { it.keyword },
            kind = null,
            declaredAt = declaredAt,
        )
    }
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
