package com.example.sample.roles

import me.tbsten.katachi.dsl.DeclarationContainerScope

/** The role of the prose that explains the repository to whoever opens it. */
fun DeclarationContainerScope.documentation() = "Documentation" {
    title = "Documentation"
    summary = "Explanations for people reading the repository, such as README.md"
    documented = false
    description = """
        Prose for whoever opens this sample. For now it is the single root `README.md`, which
        covers the module structure, how to hand over the Android SDK, which task runs what, and
        the constraints when raising versions.

        The list of roles and the description of layers are not written here. The definition
        itself holds those, and a copy would always make one of the two stale. `README.md` takes
        on only what does not come out of the definition (preparing the environment, why AGP
        cannot be raised, how to run the check).

        `documented = false`. A role for "explanations aimed at readers" does not say what this
        app is, so it is left out of the generated documentation. It is still checked, so
        deleting or renaming `README.md` is a violation.
    """.trimIndent()
    example("README.md", "The explanation of the sample")
    layout {
        "README.md".file()
    }
}
