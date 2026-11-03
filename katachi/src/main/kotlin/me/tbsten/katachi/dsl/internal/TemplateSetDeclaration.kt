package me.tbsten.katachi.dsl.internal

import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.KatachiInvalidTemplateSetException
import me.tbsten.katachi.dsl.TemplateSetScope
import me.tbsten.katachi.dsl.TemplateSetScopeImpl

internal class TemplateSetMemberDeclaration(
    val specifier: String,
    val declaredAt: DeclarationSite,
) {
    override fun toString(): String = "TemplateSetMemberDeclaration($specifier)"
}

internal class TemplateSetDeclaration(
    val name: String,
    val groupPath: List<String>,
    val title: String?,
    val summary: String?,
    val declaredAt: DeclarationSite,
    val parameters: List<DeclaredTemplateParameter>,
    val members: List<TemplateSetMemberDeclaration>,
    val block: TemplateSetScope.() -> Unit,
) {
    val specifier: String = (groupPath + name).joinToString(".")

    override fun toString(): String = "TemplateSetDeclaration($specifier)"
}

internal class TemplateSetMemberReplay(
    val specifier: String,
    val declaredAt: DeclarationSite,
    val args: Map<String, String>,
)

internal class TemplateSetReplay(
    val members: List<TemplateSetMemberReplay>,
    val missing: List<String>,
    val invalid: Map<String, InvalidTemplateValue>,
)

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
