package com.example.sample.groups

import com.example.sample.roles.gradleModule
import com.example.sample.roles.gradleRoot
import me.tbsten.katachi.dsl.DeclarationContainerScope

/**
 * Roles of the build setup: the Gradle scripts that describe how this project is assembled.
 *
 * Build files are checked but not documented: they are the same in every project and say
 * nothing about this app. `documented` is not inherited (a declared value is kept as
 * written), so each role also has to say `documented = false` for itself.
 *
 * The build's *output* needs no declaration of its own. `build/` is ignored by `.gitignore`
 * and the default `files = gitTracked()` never offers it to the check; the `build` that
 * `.module { }` adds is what makes the definition say so out loud rather than rely on it.
 */
fun DeclarationContainerScope.buildGroup() = "build".group {
    documented = false
    title = "ビルド"

    gradleModule()
    gradleRoot()
}
