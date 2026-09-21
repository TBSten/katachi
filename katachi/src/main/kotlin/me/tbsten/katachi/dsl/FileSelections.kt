package me.tbsten.katachi.dsl

import me.tbsten.katachi.fs.FileSelection

/**
 * Names for the file selections katachi ships with, written on top of [FileSelection] and
 * nothing else.
 *
 * Neither is a member of [ArchitectureScope]: each takes the scope as a context parameter,
 * which is exactly what a project writes when it gives its own [FileSelection] a name —
 * `bazelSources()` next to these two, resolved the same way and refused in the same places.
 * Nothing here is katachi's privilege.
 */

/**
 * Only the files git reports for this project. See [FileSelection.GitTracked].
 *
 * This is the default, so writing it changes nothing. It is here for a definition that would
 * rather say out loud which files it looks at than let the reader assume.
 *
 * ## Example 1: Say the default out loud
 * ```kt
 * import me.tbsten.katachi.dsl.architecture
 * import me.tbsten.katachi.dsl.gitTracked
 *
 * val projectArchitecture = architecture {
 *     files = gitTracked()
 *     "domain".group { "UseCase" { } }
 * }
 * ```
 */
context(architectureScope: ArchitectureScope)
public fun gitTracked(): FileSelection = FileSelection.GitTracked

/**
 * Every file below the project root, whatever git thinks of it. See [FileSelection.WholeTree].
 *
 * ## Example 1: Ignore git and walk the whole tree
 * ```kt
 * import me.tbsten.katachi.dsl.architecture
 * import me.tbsten.katachi.dsl.wholeTree
 *
 * val projectArchitecture = architecture {
 *     files = wholeTree()
 *     "domain".group { "UseCase" { } }
 * }
 * ```
 */
context(architectureScope: ArchitectureScope)
public fun wholeTree(): FileSelection = FileSelection.WholeTree
