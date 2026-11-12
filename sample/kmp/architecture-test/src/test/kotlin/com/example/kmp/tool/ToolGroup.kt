package com.example.kmp.tool

import com.example.kmp.tool.documentation
import com.example.kmp.tool.git
import me.tbsten.katachi.dsl.DeclarationContainerScope

fun DeclarationContainerScope.toolGroup() = "Tool".group {
    documented = false
    title = "Tool"
    summary = "Files that belong to the tooling around the project, not to the build"
    description = """
        The configuration of the tools around the project, and the `README.md` a person reads.
        Both would work if put into the `Gradle` group. They are separated because "how to build
        this project" and "what tools this project has" should not go into one bucket. Kinds of
        tooling only ever grow, and splitting later is more work than having split already.

        What belongs here:

        - Configuration told to git (`.gitignore`)
        - An explanation for whoever opens this sample (`README.md`)
        - Later, if added: linter or formatter configuration, things like `.editorconfig`

        What does not belong here:

        - Build files. `build.gradle.kts` and the wrapper belong to the `Gradle` group
        - CI configuration. This sample has no `.github/` of its own; it is run from the workflow
          at the repository root

        Like the `Gradle` group, it is `documented = false`. It exists, but it is not the
        architecture that a reader of the generated documentation is looking for.
    """.trimIndent()

    documentation()
    git()
}
