package com.example.roles

import com.example.forbiddenContents
import me.tbsten.katachi.dsl.DeclarationContainerScope

/** The role of the version control configuration. */
fun DeclarationContainerScope.git() = "Git" {
    title = "Git configuration"
    summary = "Version control configuration files"
    description = """
        The file that says what not to hand to Git. In this project only `.gitignore` falls under
        this role.

        It is not merely a working convenience; it affects the scope of katachi's check itself.
        The default `files = gitTracked()` passes only the files git tracks to the check, so
        anything listed in `.gitignore` (`build/`, `.gradle/`, `.kotlin/`, `local.properties` and
        so on) goes by without belonging to any role. Conversely, a file that is tracked but has
        no role fails.

        When more files appear, add one role to the `tool` group rather than mixing them into this
        one. The `tool` group this role belongs to is `documented = false`, so it does not appear
        in the generated documentation.
    """.trimIndent()
    forbiddenContents = "What must not be placed here is configuration for tools other than Git."
    example(".gitignore", "The list of files excluded from version control")
    layout {
        ".gitignore".file()
    }
}
