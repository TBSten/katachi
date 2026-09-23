package com.example.groups

import com.example.roles.gradle
import me.tbsten.katachi.dsl.DeclarationContainerScope

/**
 * Roles of the build definition.
 *
 * Build files are checked like everything else, but they are noise in the generated
 * documentation, so the whole group opts out. `documented` is a property written inside the
 * block, the same on a group as on a role; it is metadata like anything else a processor
 * reads, which is why it is not a parameter of `group()`.
 *
 * What the build *writes* needs no role at all: `build/` and `.kotlin/` are listed in
 * `.gitignore`, and the default `files = gitTracked()` never offers them to the check. Every
 * `.module { }` still says `"build".ignore()` out loud, because a project that chose
 * `files = wholeTree()` has no git to lean on and the reason has to be readable either way.
 */
fun DeclarationContainerScope.buildGroup() = "build".group {
    documented = false
    title = "ビルド"

    gradle()
}
