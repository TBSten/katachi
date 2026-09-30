package com.example.sample.roles

import me.tbsten.katachi.dsl.DeclarationContainerScope

/** The role of the files git itself reads. */
fun DeclarationContainerScope.git() = "Git" {
    title = "Git"
    summary = ".gitignore and the like"
    documented = false
    description = """
        Configuration files that git itself reads. For now only the single `.gitignore` at the
        repository root.

        It is more than a plain config file for katachi too. The default `files = gitTracked()`
        checks only the result of `git ls-files --cached --others --exclude-standard`, so
        whatever is ignored here never reaches the check in the first place. That is why no role
        is needed for each module's `build/`, `.gradle/`, `.kotlin/` or `local.properties`.
        Conversely, switching to `files = wholeTree()` would report all of them as `Unexpected`.

        When editing `.gitignore`, be aware that the set of checked files moves. Ignoring more
        removes files from the check, and ignoring less turns them into violations as files
        without a role.
    """.trimIndent()
    example(".gitignore", "The list of what git ignores")
    layout {
        ".gitignore".file()
    }
}
