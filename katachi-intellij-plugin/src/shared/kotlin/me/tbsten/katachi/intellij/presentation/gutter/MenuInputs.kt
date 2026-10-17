package me.tbsten.katachi.intellij.presentation.gutter

import me.tbsten.katachi.intellij.model.FileRoles
import me.tbsten.katachi.intellij.model.RoleModel
import me.tbsten.katachi.intellij.model.TemplateId

/**
 * Puts the menu's input together from what the ◆ was drawn from (design 2.3): the mark, and the definition's answer for
 * the file. Pure; the IDE side adds which definition sites it could not find.
 *
 * ```kotlin
 * val input = MenuInputs.of(mark, fileRoles)
 * val menu = MenuModel.of(input, availability)
 * ```
 */
internal object MenuInputs {
    fun of(mark: GutterMark, fileRoles: FileRoles): MenuInput {
        val file = fileRoles.projectRoot.resolve(fileRoles.relativePath)
        return MenuInput(
            title = titleOf(mark, SiblingFiles.nameOf(fileRoles.relativePath)),
            file = file,
            module = fileRoles.module,
            stale = mark.stale,
            roles = mark.roles.map { roleMark ->
                RoleMenuInput(
                    role = roleMark.role,
                    explanation = roleMark.explanation,
                    constraintSite = roleMark.constraintSite,
                    missing = roleMark.missing,
                    siblings = SiblingFiles.of(
                        fileRoles.relativePath,
                        roleMark.role.layout,
                        holding = fileRoles.matches.filter { it.role == roleMark.role }.map { it.entry },
                    ),
                    projectRoot = fileRoles.projectRoot,
                    templates = templatesOf(roleMark.role, fileRoles),
                )
            },
        )
    }

    /** `<declaration> — <roles>` for a ◆ of a declaration, `<file name> — <roles>` for the one ◆ of a file (design 2.3). */
    fun titleOf(mark: GutterMark, fileName: String): String {
        val roles = mark.roles.joinToString(", ") { it.role.qualifiedName }
        val target = mark.target
        return if (mark.placement == MarkPlacement.PerDeclaration && target is MarkTarget.Declaration) {
            "${target.declaration.name} — $roles"
        } else {
            "$fileName — $roles"
        }
    }

    /** The templates the role's layout entries carry, once each, in the order the entries are written. */
    private fun templatesOf(role: RoleModel, fileRoles: FileRoles): List<TemplateChoice> =
        role.layout.mapNotNull { it.template }
            .distinctBy { it.specifier }
            .map { TemplateChoice(TemplateId(fileRoles.module.id, it.specifier), it.title ?: it.specifier) }
}
