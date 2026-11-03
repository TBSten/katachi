package me.tbsten.katachi.dsl

/**
 * Only the files git reports for this project. See [FileSelection.GitTracked].
 *
 * This is the default, so writing it changes nothing; write it to make the choice explicit.
 *
 * ## Example 1: Say the default out loud
 * ```kt
 * import me.tbsten.katachi.dsl.architecture
 * import me.tbsten.katachi.dsl.gitTracked
 *
 * val projectArchitecture = architecture {
 *     files = gitTracked()
 *     "domain".group { "UseCase".role { } }
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
 *     "domain".group { "UseCase".role { } }
 * }
 * ```
 */
context(architectureScope: ArchitectureScope)
public fun wholeTree(): FileSelection = FileSelection.WholeTree
