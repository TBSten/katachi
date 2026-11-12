package com.example.sample.tool

import com.example.sample.tool.documentation
import com.example.sample.tool.git
import me.tbsten.katachi.dsl.DeclarationContainerScope

fun DeclarationContainerScope.toolGroup() = "Tool".group {
    documented = false
    title = "Tools"
    summary = "Tools the repository carries that are neither the app nor the build"
    description = """
        Files at the repository root that are neither the app nor build configuration. For now
        that is `.gitignore`, which git reads, and `README.md`, which people read. When CI
        settings or formatter settings appear, add a role and put it here.

        It is kept apart from the `Gradle` group. A group can be declared only once, and each
        file here belongs to a different tool, so lumping them together as "build" would leave
        no way to tell which role the next file belongs to.

        `documented = false`, like the build configuration, because these files do not say
        what this app is.

        This group is also used to prove the check is not passing by accident.
        `ProjectArchitectureSpec` builds a definition without `toolGroup()` and checks that
        `.gitignore` and `README.md` show up as two more `[UnexpectedFile]` violations (the
        ones held back by the baseline appear either way). If removing the group produced no
        violations, the check would not be walking anything.
    """.trimIndent()

    git()
    documentation()
}
