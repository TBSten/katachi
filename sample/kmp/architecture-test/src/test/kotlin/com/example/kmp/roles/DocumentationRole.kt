package com.example.kmp.roles

import me.tbsten.katachi.dsl.DeclarationContainerScope

/** The role of the prose that explains this sample to whoever opens it. */
fun DeclarationContainerScope.documentation() = "Documentation" {
    title = "Documentation"
    summary = "README.md and the like, explanations for people reading the repository"
    documented = false
    description = """
        Prose for whoever opens this sample. Right now it is the root `README.md` alone, which
        says what kind of sample this is, which files to read and what each command runs.

        Do not write the list of roles or the description of layers here. The definition itself
        holds them, and a copy would always leave one side stale. `README.md` takes on only what
        the definition cannot say (the aim of this sample, where to start reading, how to run the checks).

        `documented = false`. The role "explanation for readers" does not explain what this app
        is, so it is left out of the generated documentation. It is still checked: deleting or
        renaming `README.md` is a violation.
    """.trimIndent()
    example("README.md", "The explanation of the sample")
    layout {
        "README.md".file()
    }
}
