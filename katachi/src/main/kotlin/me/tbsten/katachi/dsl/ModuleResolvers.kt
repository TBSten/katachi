package me.tbsten.katachi.dsl

import me.tbsten.katachi.check.ModuleResolver

/**
 * Names for the module resolvers katachi ships with, written on top of [ModuleResolver] and
 * nothing else.
 *
 * There is one of them, and it is not a member of [ArchitectureScope]: it takes the scope as
 * a context parameter, which is what a build that places its modules by some other rule
 * writes to give its own resolver a name of the same shape.
 */

/**
 * `:core:data` lives in `core/data`. See [ModuleResolver.Conventional].
 *
 * This is the default, and it is right for every build that has not customised `projectDir`.
 * A build that has replaces it with a [ModuleResolver] of its own.
 *
 * ## Example 1: Say the default out loud
 * ```kt
 * import me.tbsten.katachi.dsl.architecture
 * import me.tbsten.katachi.dsl.conventionalModuleResolver
 *
 * val projectArchitecture = architecture {
 *     moduleResolver = conventionalModuleResolver()
 *     "domain".group { "UseCase" { } }
 * }
 * ```
 *
 * ## Example 2: Place one module somewhere else
 * ```kt
 * import me.tbsten.katachi.check.ModuleResolver
 * import me.tbsten.katachi.dsl.architecture
 *
 * val projectArchitecture = architecture {
 *     moduleResolver = ModuleResolver { module ->
 *         if (module.value == ":app") "apps/android" else module.segments.joinToString("/")
 *     }
 *     "domain".group { "UseCase" { } }
 * }
 * ```
 */
context(architectureScope: ArchitectureScope)
public fun conventionalModuleResolver(): ModuleResolver = ModuleResolver.Conventional
