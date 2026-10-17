package me.tbsten.katachi.dsl.internal

import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.KatachiInvalidTemplateSetException
import me.tbsten.katachi.dsl.TemplateSetScope
import me.tbsten.katachi.dsl.TemplateSetScopeImpl

/** One `template("...")` of a template set: what it names and where it was written. */
internal class TemplateSetMemberDeclaration(
    val specifier: String,
    val declaredAt: DeclarationSite,
) {
    override fun toString(): String = "TemplateSetMemberDeclaration($specifier)"
}

/**
 * A `"name".templateSet { }` as the definition keeps it: what the one declaring replay found
 * (the parameters and the members) and the block, which later replays run again.
 */
internal class TemplateSetDeclaration(
    val name: String,
    val groupPath: List<String>,
    val title: String?,
    val summary: String?,
    val declaredAt: DeclarationSite,
    /** In declaration order, with types and defaults. */
    val parameters: List<DeclaredTemplateParameter>,
    /** In the order they were written. */
    val members: List<TemplateSetMemberDeclaration>,
    val block: TemplateSetScope.() -> Unit,
) {
    /** The group path and [name] joined with `.`, which is what `--arg template=` takes. */
    val specifier: String = (groupPath + name).joinToString(".")

    override fun toString(): String = "TemplateSetDeclaration($specifier)"
}

/** What one member was given by one replay: its `arg` values by name, in the order written. */
internal class TemplateSetMemberReplay(
    val specifier: String,
    val declaredAt: DeclarationSite,
    val args: Map<String, String>,
)

/**
 * What a Run or Preview replay of a template set came to.
 *
 * [missing] and [invalid] are what the set's own parameters could not supply; they are collected
 * rather than thrown so that the caller reports every one of them at once.
 */
internal class TemplateSetReplay(
    val members: List<TemplateSetMemberReplay>,
    val missing: List<String>,
    val invalid: Map<String, InvalidTemplateValue>,
)

/**
 * Replays [declaration] in [mode], which is Run or Preview.
 *
 * @throws KatachiInvalidTemplateSetException when the block no longer declares the members it
 *   declared the first time, or reads a value outside a member, or passes one name twice.
 */
internal fun replayTemplateSet(declaration: TemplateSetDeclaration, mode: TemplateSetMode): TemplateSetReplay {
    val scope = TemplateSetScopeImpl(declaration.specifier, mode, declaration.declaredAt)
    declaration.block(scope)
    scope.requireWellFormed()
    val replayed = scope.members()
    val declared = declaration.members.map { it.specifier }
    if (replayed.map { it.specifier } != declared) {
        throw KatachiInvalidTemplateSetException(
            templateSet = declaration.specifier,
            problem = KatachiInvalidTemplateSetException.Problem.MembersChanged,
            name = null,
            declaredAt = declaration.declaredAt,
            firstDeclaredAt = null,
        )
    }
    return TemplateSetReplay(members = replayed, missing = scope.missingNames(), invalid = scope.invalidValues())
}
