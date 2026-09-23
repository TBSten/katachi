package com.example.sample.roles

import me.tbsten.katachi.dsl.DeclarationContainerScope

/** The role of the files git itself reads. */
fun DeclarationContainerScope.git() = "Git" {
    // No `title` here on purpose: an undocumented role has no display name to show,
    // and the sample asserts that the role name is then used as-is.
    summary = ".gitignore など"
    documented = false
    layout {
        ".gitignore".file()
    }
}
