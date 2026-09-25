package me.tbsten.katachi.template.internal

import me.tbsten.katachi.dsl.Role
import me.tbsten.katachi.dsl.internal.TemplateDeclaration
import me.tbsten.katachi.template.KatachiAmbiguousTemplateRoleException
import me.tbsten.katachi.template.KatachiNoTemplateException
import me.tbsten.katachi.template.KatachiUnknownTemplateRoleException

/**
 * The role `--arg roleName=` named.
 *
 * A qualified name (`domain/UseCase`) is tried first and a plain one (`UseCase`) second, so the
 * short form works while a project has one role of that name and stops working -- loudly -- the
 * moment a second group declares one too. Answering with either of them would make the command
 * line mean two things at once.
 */
internal fun templateRoleOf(roles: List<Role>, roleName: String): Role {
    roles.firstOrNull { it.qualifiedName == roleName }?.let { return it }

    val byName = roles.filter { it.name == roleName }
    return when (byName.size) {
        1 -> byName.single()
        0 -> throw KatachiUnknownTemplateRoleException(
            roleName = roleName,
            declaredRoles = roles.map { it.qualifiedName }.sorted(),
        )

        else -> throw KatachiAmbiguousTemplateRoleException(
            roleName = roleName,
            candidates = byName.map { it.qualifiedName }.sorted(),
        )
    }
}

/**
 * The one `template { }` of [role].
 *
 * `templates` holds at most one -- a second is refused where it is written -- so the only thing
 * left to say here is that a role has none.
 */
internal fun templateOf(role: Role): TemplateDeclaration = role.templates.firstOrNull()
    ?: throw KatachiNoTemplateException(role = role.qualifiedName, declaredAt = role.declaredAt)
