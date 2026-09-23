package com.example.groups

import com.example.roles.git
import me.tbsten.katachi.dsl.DeclarationContainerScope

/**
 * Roles of the tools around the project that are not the build itself.
 *
 * Like the build group, these are checked but kept out of the generated documentation.
 */
fun DeclarationContainerScope.toolGroup() = "tool".group {
    documented = false
    title = "ツール設定"

    git()
}
