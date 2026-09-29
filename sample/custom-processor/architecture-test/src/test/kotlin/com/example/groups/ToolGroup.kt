package com.example.groups

import com.example.roles.documentation
import com.example.roles.git
import me.tbsten.katachi.dsl.DeclarationContainerScope

/**
 * Roles of what sits around the project without being the build or the application.
 *
 * Like the `Gradle` group `gradle()` declares, these are checked but kept out of the generated documentation.
 */
fun DeclarationContainerScope.toolGroup() = "tool".group {
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
