package me.tbsten.katachi.dsl.internal

import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.GradleScopeImpl
import me.tbsten.katachi.dsl.GroupScope
import me.tbsten.katachi.dsl.GroupScopeImpl
import me.tbsten.katachi.dsl.RoleScope
import me.tbsten.katachi.dsl.TemplateSetScope

internal fun GroupScope.declareRoleAt(name: String, declaredAt: DeclarationSite, block: RoleScope.() -> Unit): Unit =
    when (this) {
        is GroupScopeImpl -> declareRoleAt(name, declaredAt, block)
        is GradleScopeImpl -> group.declareRoleAt(name, declaredAt, block)
    }

internal fun GroupScope.declareGroupAt(name: String, declaredAt: DeclarationSite, block: GroupScope.() -> Unit): Unit =
    when (this) {
        is GroupScopeImpl -> declareGroupAt(name, declaredAt, block)
        is GradleScopeImpl -> group.declareGroupAt(name, declaredAt, block)
    }

internal fun GroupScope.declareTemplateSetAt(
    name: String,
    declaredAt: DeclarationSite,
    block: TemplateSetScope.() -> Unit,
): Unit = when (this) {
    is GroupScopeImpl -> declareTemplateSetAt(name, declaredAt, block)
    is GradleScopeImpl -> group.declareTemplateSetAt(name, declaredAt, block)
}
