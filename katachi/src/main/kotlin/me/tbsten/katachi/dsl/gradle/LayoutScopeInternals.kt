@file:Suppress("DEPRECATION_ERROR")

package me.tbsten.katachi.dsl.gradle

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.KatachiUnsupportedLayoutScopeException
import me.tbsten.katachi.dsl.LayoutDirectoryScope
import me.tbsten.katachi.dsl.LayoutModule
import me.tbsten.katachi.dsl.LayoutScope
import me.tbsten.katachi.dsl.ModuleAwareLayoutScope
import me.tbsten.katachi.dsl.internal.MODULE_SCOPE_DEPRECATION

/**
 * The module this scope is being evaluated for, such as `":feature:home"`, or `null` outside a module block.
 *
 * **Deprecated** since 0.3, removed in 0.5. See [expandModulePath] for what to write instead.
 *
 * ## Example 1: Write a custom description that names the current module
 * ```kt
 * import me.tbsten.katachi.ExperimentalKatachiApi
 * import me.tbsten.katachi.dsl.LayoutDirectoryScope
 * import me.tbsten.katachi.dsl.gradle.currentModulePath
 *
 * @OptIn(ExperimentalKatachiApi::class)
 * public fun LayoutDirectoryScope.describeCurrentModule() {
 *     description = currentModulePath?.let { "Part of $it" } ?: "Not inside a module"
 * }
 * ```
 */
@Deprecated(message = MODULE_SCOPE_DEPRECATION, level = DeprecationLevel.ERROR)
@ExperimentalKatachiApi
public val LayoutScope.currentModulePath: String?
    get() = moduleAware().currentModulePath

/**
 * What the module path's wildcards captured for the module being evaluated, or `null` outside a module block.
 *
 * **Deprecated** since 0.3, removed in 0.5. See [expandModulePath] for what to write instead.
 *
 * See [wildcards], which is this with the error message.
 *
 * ## Example 1: Write a nullable-safe alternative to `wildcards`
 * ```kt
 * import me.tbsten.katachi.ExperimentalKatachiApi
 * import me.tbsten.katachi.dsl.LayoutScope
 * import me.tbsten.katachi.dsl.gradle.currentWildcards
 *
 * @OptIn(ExperimentalKatachiApi::class)
 * public val LayoutScope.wildcardsOrEmpty: List<String>
 *     get() = currentWildcards ?: emptyList()
 * ```
 */
@Deprecated(message = MODULE_SCOPE_DEPRECATION, level = DeprecationLevel.ERROR)
@ExperimentalKatachiApi
public val LayoutScope.currentWildcards: List<String>?
    get() = moduleAware().currentWildcards

/**
 * [currentWildcards] by the names the module key gave them, or `null` outside a module block and
 * inside one whose key named nothing.
 *
 * **Deprecated** since 0.3, removed in 0.5. See [expandModulePath] for what to write instead.
 *
 * See [wildcard], which reads one of these with the error message.
 *
 * ## Example 1: Read a named wildcard, falling back when the key named nothing
 * ```kt
 * import me.tbsten.katachi.ExperimentalKatachiApi
 * import me.tbsten.katachi.dsl.LayoutScope
 * import me.tbsten.katachi.dsl.gradle.currentCaptures
 *
 * @OptIn(ExperimentalKatachiApi::class)
 * public fun LayoutScope.wildcardOrNull(name: String): String? = currentCaptures?.get(name)
 * ```
 */
@Deprecated(message = MODULE_SCOPE_DEPRECATION, level = DeprecationLevel.ERROR)
@ExperimentalKatachiApi
public val LayoutScope.currentCaptures: Map<String, String>?
    get() = moduleAware().currentCaptures

/**
 * Runs [block] once per module [modulePath] stands for, below that module's own directory.
 *
 * **Deprecated** since 0.3, removed in 0.5, with [module]. A module sugar of a project's own
 * goes with it: write the module's directory in the layout instead.
 *
 * This is the whole of what [module] does, and the one piece of
 * it that cannot be written through the public vocabulary: resolving a module path needs the
 * project's module index, which a layout scope holds and does not hand out.
 *
 * Each expansion is given the two lines every Gradle module has — `"build".ignore()` and
 * `"build.gradle".ktsFile()` — before [block] runs, so what this declares is the same
 * declaration a hand written directory block would make.
 *
 * ## Example 1: Write the module's directory instead of a module sugar of your own
 * ```kt
 * import me.tbsten.katachi.dsl.kotlin.ktFile
 *
 * // Before (0.2):
 * //   context(layoutScope: LayoutScope)
 * //   fun String.gradleModule(block: LayoutDirectoryScope.() -> Unit) = layoutScope.expandModulePath(this, block)
 * //   ":core:data".gradleModule { "*Repository".ktFile() }
 * "core/data" { "*Repository".ktFile() }
 * ```
 *
 * @param modulePath a Gradle module path, possibly holding `*` or `**`.
 * @throws me.tbsten.katachi.dsl.KatachiGlobSyntaxException when the module path cannot be read.
 * @throws me.tbsten.katachi.dsl.KatachiModuleOutsideLayoutRootException when this scope is not
 *   the root of a `layout { }` block: a module path is resolved below the project root, so a
 *   directory around it would quietly be prepended to the answer.
 */
@Deprecated(message = MODULE_SCOPE_DEPRECATION, level = DeprecationLevel.ERROR)
@ExperimentalKatachiApi
public fun LayoutScope.expandModulePath(
    modulePath: String,
    block: LayoutDirectoryScope.() -> Unit,
): LayoutModule = moduleAware().expandModulePath(modulePath, block)

private fun LayoutScope.moduleAware(): ModuleAwareLayoutScope =
    this as? ModuleAwareLayoutScope
        ?: throw KatachiUnsupportedLayoutScopeException(this::class.simpleName ?: "unknown scope")
