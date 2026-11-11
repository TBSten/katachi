package com.example.tool

import com.example.tool.documentation
import com.example.tool.git
import me.tbsten.katachi.dsl.DeclarationContainerScope

fun DeclarationContainerScope.toolGroup() = "Tool".group {
    documented = false
    title = "Tool configuration"
    summary = "Things around the repository that are neither the build nor the app"

    description = """
        Things other than the build that sit in the repository and support development. Right now it
        holds two: Git configuration (`.gitignore`) and handwritten documentation (`README.md`).

        As with the `Gradle` group, `documented = false` means they are checked but not shown in the
        generated documentation. `README.md` is not put in `docs/` because it overlaps with the
        content of `docs/`. What a reader opens first is the root `README.md`, and the index of the
        generated pages is linked from inside it.

        When editor or CI configuration files appear, the intended way to grow is to add one role to
        this group rather than mixing them into the `.gitignore` role.
    """.trimIndent()

    git()
    documentation()
}
