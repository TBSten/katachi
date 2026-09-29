package com.example.roles

import com.example.forbiddenContents
import me.tbsten.katachi.dsl.DeclarationContainerScope

/** The role of the version control configuration. */
fun DeclarationContainerScope.git() = "Git" {
    title = "Git configuration"
    summary = "Version control configuration files"
    description = """
        A file that says what not to hand to Git. In this project only `.gitignore` belongs to this
        role.

        This is not just a matter of working convenience; it affects the range katachi checks. The
        default `files = gitTracked()` passes only the files Git tracks to the check, so whatever
        `.gitignore` lists (`build/`, `.gradle/`, `.kotlin/`, `local.properties` and so on) passes by
        without belonging to any role. Conversely, a tracked file that has no role fails.

        When more files appear, do not mix them into the `.gitignore` role; add one role to the `tool`
        group. The `tool` group this role belongs to has `documented = false`, so it does not appear in
        the generated documentation.
    """.trimIndent()
    forbiddenContents = "Configuration of tools other than Git must not be placed here."
    example(".gitignore", "The list of files excluded from version control")
    layout {
        ".gitignore".file()
    }
}
