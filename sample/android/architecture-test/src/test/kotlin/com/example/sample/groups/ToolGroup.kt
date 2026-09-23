package com.example.sample.groups

import com.example.sample.roles.documentation
import com.example.sample.roles.git
import me.tbsten.katachi.dsl.DeclarationContainerScope

/**
 * Roles of the tools a repository carries that are neither the app nor its build: git and
 * the prose that explains the sample, plus whatever else earns a place later (CI,
 * formatters, editor settings).
 *
 * They sit in their own group rather than in `build`, because a group is declared exactly
 * once and each of these files belongs to a different tool. Like the build scripts, they
 * are checked but not documented.
 *
 * This group is also what `ProjectArchitectureSpec` leaves out to prove the check is not
 * passing by accident: drop it and exactly the two files it covers turn into violations.
 */
fun DeclarationContainerScope.toolGroup() = "tool".group {
    documented = false
    title = "ツール"

    git()
    documentation()
}
