package com.example.sample.groups

import com.example.sample.roles.documentation
import com.example.sample.roles.git
import me.tbsten.katachi.dsl.DeclarationContainerScope

/**
 * Roles of the tools a repository carries that are neither the app nor its build: git and
 * the prose that explains the sample, plus whatever else earns a place later (CI,
 * formatters, editor settings).
 *
 * They sit in their own group rather than in katachi's `Gradle` group, because a group is
 * declared exactly once and each of these files belongs to a different tool. Like the build
 * scripts, they are checked but not documented.
 *
 * This group is also what `ProjectArchitectureSpec` leaves out to prove the check is not
 * passing by accident: drop it and the two files it covers turn into violations, on top of the
 * ones the baseline holds back, which are there either way.
 */
fun DeclarationContainerScope.toolGroup() = "tool".group {
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
