package com.example.kmp.groups

import com.example.kmp.roles.git
import me.tbsten.katachi.dsl.DeclarationContainerScope

/**
 * Files that belong to the tools around the project rather than to the build. Split out of
 * the `build` group so that "how this project is built" and "what tooling it carries" do not
 * share one bucket.
 *
 * Undocumented for the same reason the build group is: real, but not part of the
 * architecture a reader of the generated docs is looking for.
 */
fun DeclarationContainerScope.toolGroup() = "tool".group {
    documented = false
    title = "ツール"

    git()
}
