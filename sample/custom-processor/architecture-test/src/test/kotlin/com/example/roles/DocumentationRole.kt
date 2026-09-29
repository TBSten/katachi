package com.example.roles

import com.example.allowedContents
import com.example.forbiddenContents
import me.tbsten.katachi.dsl.DeclarationContainerScope

/** The role of the prose a human writes for whoever opens this sample. */
fun DeclarationContainerScope.documentation() = "Documentation" {
    title = "Handwritten documentation"
    summary = "README.md and similar: explanations for whoever opens this sample"
    description = """
        Prose that a person writes and commits. Right now it is the single root `README.md`, which
        explains what this sample shows with its three processors.

        It is the exact counterpart of the generated documentation role. `docs/` is written out of the
        definition and loses any manual edit, while `README.md` is written by hand and never touched by
        generation.

        `layout { }` requires exactly the name `README.md`. Deleting or renaming it is a violation.
    """.trimIndent()
    allowedContents = """
        Only what cannot come out of the definition belongs here. An explanation of a role goes in its
        `description`, which appears in `docs/`, so it is not repeated in the README. A copy would
        always leave one of the two stale.
    """.trimIndent()
    forbiddenContents = """
        - Copies of the explanation of a role or a group. The single source is the `description` on the
          definition side
        - Pages that should be in `docs/`. Generated output belongs to the generated documentation role
    """.trimIndent()
    example("README.md", "An explanation of the sample")
    layout {
        "README.md".file()
    }
}
