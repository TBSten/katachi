package com.example.roles

import me.tbsten.katachi.dsl.DeclarationContainerScope

/** The role of the prose that explains this sample to whoever opens it. */
fun DeclarationContainerScope.documentation() = "Documentation" {
    title = "Documentation"
    summary = "README.md and other explanations for people reading the repository"
    documented = false
    description = """
        Prose for whoever opens this sample. For now it is the single root `README.md`, which says
        what kind of sample this is, which files to read, and which command runs what.

        The list of roles and the description of the layers are not written here. The definition
        itself holds them, and keeping a copy means one of the two will always go stale. What
        `README.md` takes on is limited to what the definition cannot say: the aim of this sample,
        where to start reading, and how to run the checks.

        `documented = false`. A role for explanations aimed at readers does not describe what this
        app is, so it does not appear in the generated documentation. It is still checked, so
        deleting or renaming `README.md` is a violation.
    """.trimIndent()
    example("README.md", "The description of the sample")
    layout {
        "README.md".file()
    }
}
