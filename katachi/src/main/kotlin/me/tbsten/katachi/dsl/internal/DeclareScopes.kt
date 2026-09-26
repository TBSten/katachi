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

/**
 * Validates the name, takes it in [declaredNames], then evaluates [block] to collect the
 * nested groups, the roles and the metadata.
 *
 * The name is reserved before [block] runs so that a block which reaches back into this
 * same scope cannot slip a second declaration of the same name past the check.
 */
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
    )
}

/**
 * Validates the name, takes it in [declaredNames], then evaluates [block] to collect the
 * role's properties.
 *
 * The name is reserved before [block] runs, for the same reason as in `declareGroup`.
 *
 * @throws KatachiFileConstraintWithoutLayoutException when the role wrote a constraint but no
 *   `layout { }`. It is decided here, at the end of the block, because that is the first
 *   moment both lists are complete — and it needs nothing else: no file is read, and no
 *   module index is consulted.
 */
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
        templates = scope.templates.toList(),
        groupPath = groupPath,
        declaredAt = declaredAt,
    )
}
