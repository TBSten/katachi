package com.example.kmp.roles

import com.example.kmp.forbiddenContents
import com.example.kmp.processors.owner
import me.tbsten.katachi.dsl.DeclarationContainerScope

/**
 * What git needs to be told about this project.
 *
 * No `title` here on purpose: an undocumented role has no display name to show, so this is the
 * one place in this sample that exercises the default — the role name itself.
 * `ProjectArchitectureSpec` asserts it.
 */
fun DeclarationContainerScope.git() = "Git" {
    summary = ".gitignore. Keeps build outputs and Xcode working files out of Git"
    documented = false
    // Also `owner = "platform"` (see com.example.kmp.processors.Owner), this sample's own
    // metadata key -- not katachi's. `PlatformOwnedFilesSpec` builds a variant of
    // this exact role with the tag left out to prove its processor really reads it.
    owner = "platform"
    description = """
        A role that holds only what is told to git. Right now that is the single `.gitignore`,
        which keeps `build/`, `.gradle/`, `.kotlin/`, `local.properties`, and Xcode's
        `xcuserdata/` and `DerivedData/` out of Git.

        This role underpins the checks of this sample itself. `files` is left at the default
        `gitTracked()`, so only files that git tracks reach katachi. That means no role has to be
        written for `build/`; conversely, the moment `.gitignore` is loosened, generated files
        start to be reported as "files no role claims".

        It is also the only declaration in this sample without a `title`. It is left that way to
        try out what shows when the display name is omitted (the role name is used as it is),
        and `ProjectArchitectureSpec` pins that down.
    """.trimIndent()
    forbiddenContents = """
        - Build files. `build.gradle.kts` and the wrapper belong to the `Gradle` group
        - CI configuration. This sample has no `.github/` of its own; it is run from the workflow
          at the repository root
    """.trimIndent()
    example(".gitignore", "The list of what Git ignores")
    layout {
        ".gitignore".file()
    }
}
