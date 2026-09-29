package com.example.groups

import com.example.roles.documentation
import com.example.roles.git
import me.tbsten.katachi.dsl.DeclarationContainerScope

/**
 * Roles of the tools around the project that are not the build itself.
 *
 * Like the `Gradle` group, these are checked but kept out of the generated documentation.
 */
fun DeclarationContainerScope.toolGroup() = "tool".group {
    documented = false
    title = "Tool configuration"
    summary = "Configuration of tools around the project that are not the build itself"

    description = """
        Things kept in the repository that support development but are not the build. For now
        this holds two roles: the Git configuration (`.gitignore`) and the `README.md` that people
        read.

        Like the build, it is `documented = false`: it is checked but does not appear in the
        generated documentation. When editor or CI configuration files appear, the intended way to
        grow is to add one role to this group rather than mixing them into the `.gitignore` role.
    """.trimIndent()

    documentation()
    git()
}
