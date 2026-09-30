package com.example.sample.groups

import com.example.sample.roles.repositoryImplementation
import com.example.sample.roles.repositoryInterface
import me.tbsten.katachi.dsl.DeclarationContainerScope

/** Roles of the data layer: what `:data` holds. */
fun DeclarationContainerScope.dataGroup() = "data".group {
    title = "Data layer"
    summary = "What :data holds: fetching and storing data"
    description = """
        The `:data` module. For now it has two roles, RepositoryInterface and
        RepositoryImplementation, with the interface and its implementation side by side in one
        package per subject (`user` / `settings`). Each package is one entry of `DataDomain`; to
        add a subject, add one line there.

        It is the lowest layer of this app and depends on no other module. It touches neither
        Android nor Compose, so it can be read without pulling in `:ui` or `:feature:*`. A
        ViewModel receives the interface here through its constructor, and tests swap it for a
        fake from `:testing`.

        It is a group even though it is small because this is where things will grow. Data sources
        or DTOs go here when they need to be split out. For now, any file in `:data` other than
        a repository interface or implementation is a violation, so there is no "put it down first
        and think later".
    """.trimIndent()

    repositoryInterface()
    repositoryImplementation()
}

/**
 * What `:data` keeps a package for, by name: `User` is the `user` package.
 *
 * Read twice, so the list is written once. The Repository roles' `layout { }` declare one
 * package per entry, and requires every file in it to start with that entry's name
 * (`user/User*Repository.kt`); each entry's own id (`--arg template=data.RepositoryInterface.user`) is
 * what the Repository templates take, and Fake reads the same name back from `repository`.
 *
 * A fixed list rather than a directory `capture(...)` is what lets `layout { }` require that:
 * unlike a module key, whose `wildcard(...)` a layout can read back while it is still being
 * declared, a directory capture's value is not something the layout itself can read -- there is
 * no equivalent for a `capture(...)` level, only for a module's own `*`. So "every file below
 * `user/` starts with `User`" cannot be written as one pattern parameterised by a capture; each
 * domain's own literal package and prefix has to be written out. Choosing where a generated file
 * goes, rather than constraining its name, is the one thing a plain `capture(...)` would still do.
 */
enum class DataDomain {
    User,
    Settings,
    ;

    /** The package below `com.example.sample.data`, `user`. */
    val packageName: String get() = name.lowercase()

    /** The id both Repository templates answer to for this domain: `user` for [User]. */
    val templateId: String get() = name.replaceFirstChar(Char::lowercaseChar)
}
