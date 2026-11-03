package me.tbsten.katachi.dsl.internal

import me.tbsten.katachi.dsl.DeclarationKind
import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.Group
import me.tbsten.katachi.dsl.GroupScope
import me.tbsten.katachi.dsl.GroupScopeImpl
import me.tbsten.katachi.dsl.KatachiFileConstraintWithoutLayoutException
import me.tbsten.katachi.dsl.Role
import me.tbsten.katachi.dsl.RoleScope
import me.tbsten.katachi.dsl.RoleScopeImpl
import me.tbsten.katachi.dsl.TemplateSetScope
import me.tbsten.katachi.dsl.TemplateSetScopeImpl

// The name is reserved before the block runs so a block reaching back into this scope cannot declare it twice.
internal fun declareGroup(
    name: String,
    parentPath: List<String>,
    declaredAt: DeclarationSite,
    declaredNames: DeclaredNames,
    block: GroupScope.() -> Unit,
): Group {
    requireValidIdentifier(name, DeclarationKind.Group, declaredAt)
    requireNameIsFree(declaredNames, DeclarationKind.Group, name, parentPath, declaredAt)
    declaredNames.reserve(name, DeclarationKind.Group, declaredAt)
    val path = parentPath + name
    val scope = GroupScopeImpl(path)
    scope.block()
    return Group(
        name = name,
        metadata = scope.metadata.build(),
        path = path,
        groups = scope.groups.toList(),
        roles = scope.roles.toList(),
        declaredAt = declaredAt,
        templateSets = scope.templateSets.toList(),
    )
}

// Reserved before the block runs, as in declareGroup. The layout-less constraint check lives here: the first point
// where both lists are complete.
internal fun declareRole(
    name: String,
    groupPath: List<String>,
    declaredAt: DeclarationSite,
    declaredNames: DeclaredNames,
    block: RoleScope.() -> Unit,
): Role {
    requireValidIdentifier(name, DeclarationKind.Role, declaredAt)
    requireNameIsFree(declaredNames, DeclarationKind.Role, name, groupPath, declaredAt)
    declaredNames.reserve(name, DeclarationKind.Role, declaredAt)
    val scope = RoleScopeImpl(name)
    scope.block()
    // A wildcard module key that currently matches nothing still counts as a layout: such a
    // role is a place waiting to fill up, which is the same thing `flattenLayout` already
    // decides when it leaves those declarations out of `required`.
    if (scope.fileConstraints.isNotEmpty() && scope.layouts.isEmpty()) {
        throw KatachiFileConstraintWithoutLayoutException(
            role = name,
            declaredAt = scope.fileConstraints.first().declaredAt,
        )
    }
    return Role(
        name = name,
        metadata = scope.metadata.build(),
        layouts = scope.layouts.toList(),
        fileConstraints = scope.fileConstraints.toList(),
        groupPath = groupPath,
        declaredAt = declaredAt,
    )
}

// Reserved before the block runs. Member blocks are not run here: they need values that do not exist yet.
internal fun declareTemplateSet(
    name: String,
    groupPath: List<String>,
    declaredAt: DeclarationSite,
    declaredNames: DeclaredNames,
    block: TemplateSetScope.() -> Unit,
): TemplateSetDeclaration {
    requireValidIdentifier(name, DeclarationKind.TemplateSet, declaredAt)
    requireNameIsFree(declaredNames, DeclarationKind.TemplateSet, name, groupPath, declaredAt)
    declaredNames.reserve(name, DeclarationKind.TemplateSet, declaredAt)
    val specifier = (groupPath + name).joinToString(".")
    val scope = TemplateSetScopeImpl(specifier, TemplateSetMode.Declare, declaredAt)
    scope.block()
    scope.requireWellFormed()
    return TemplateSetDeclaration(
        name = name,
        groupPath = groupPath,
        title = scope.title,
        summary = scope.summary,
        declaredAt = declaredAt,
        parameters = scope.parameters(),
        members = scope.memberDeclarations(),
        block = block,
    )
}
