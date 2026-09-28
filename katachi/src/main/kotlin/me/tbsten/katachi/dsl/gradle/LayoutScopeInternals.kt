package me.tbsten.katachi.dsl.gradle

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.KatachiUnsupportedLayoutScopeException
import me.tbsten.katachi.dsl.LayoutDirectoryScope
import me.tbsten.katachi.dsl.LayoutModule
import me.tbsten.katachi.dsl.LayoutScope
import me.tbsten.katachi.dsl.ModuleAwareLayoutScope

/**
 * The part of a layout scope a utility layer cannot reach through the public vocabulary.
 *
 * [LayoutScope] declares what a layout *says*; these three hand out what the scope *knows*
 * while it is being evaluated, which [ModuleAwareLayoutScope] declares. `"...".ktFile()`
 * needs none of them — it is written against `file()` alone — but the Gradle vocabulary in
 * this package does: expanding `":feature:*"` needs the project's modules, and
 * `modulePackage` and `wildcards` need the module the block is currently being evaluated for.
 *
 * They are extensions on [LayoutScope] rather than plain members of [ModuleAwareLayoutScope]
 * because `context(layoutScope: LayoutScope)` is the shape every utility is written in, a
 * project's own as much as katachi's. They are public for the same reason: a project whose
 * build is unusual enough to need its own `.module { }` should be able to write one. They are
 * [ExperimentalKatachiApi] because what a scope knows grows with the DSL, and the opt-in is
 * what says their shape can still change.
 */

/**
 * The module this scope is being evaluated for, as katachi prints it (`":feature:home"`),
 * or `null` directly under `layout { }` and inside a plain directory block.
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
@ExperimentalKatachiApi
public val LayoutScope.currentModulePath: String?
    get() = moduleAware().currentModulePath

/**
 * What the module path's wildcards captured for the module being evaluated, or `null`
 * outside a module block, where there is no module path to have captured anything.
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
@ExperimentalKatachiApi
public val LayoutScope.currentWildcards: List<String>?
    get() = moduleAware().currentWildcards

/**
 * [currentWildcards] by the names the module key gave them, or `null` outside a module block and
 * inside one whose key named nothing.
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
@ExperimentalKatachiApi
public val LayoutScope.currentCaptures: Map<String, String>?
    get() = moduleAware().currentCaptures

/**
 * Runs [block] once per module [modulePath] stands for, below that module's own directory.
 *
 * This is the whole of what [module] does, and the one piece of
 * it that cannot be written through the public vocabulary: resolving a module path needs the
 * project's module index, which a layout scope holds and does not hand out.
 *
 * Each expansion is given the two lines every Gradle module has — `"build".ignore()` and
 * `"build.gradle".ktsFile()` — before [block] runs, so what this declares is the same
 * declaration a hand written directory block would make.
 *
 * ## Example 1: Write your own alias for `.module { }`
 * ```kt
 * import me.tbsten.katachi.ExperimentalKatachiApi
 * import me.tbsten.katachi.dsl.LayoutDirectoryScope
 * import me.tbsten.katachi.dsl.LayoutModule
 * import me.tbsten.katachi.dsl.LayoutScope
 * import me.tbsten.katachi.dsl.gradle.expandModulePath
 *
 * // This is exactly what `"...".module { }` does; a project can spell it however it likes.
 * @OptIn(ExperimentalKatachiApi::class)
 * context(layoutScope: LayoutScope)
 * public fun String.gradleModule(block: LayoutDirectoryScope.() -> Unit): LayoutModule =
 *     layoutScope.expandModulePath(this, block)
 * ```
 *
 * @param modulePath a Gradle module path, possibly holding `*` or `**`.
 * @throws me.tbsten.katachi.dsl.KatachiGlobSyntaxException when the module path cannot be read.
 * @throws me.tbsten.katachi.dsl.KatachiModuleOutsideLayoutRootException when this scope is not
 *   the root of a `layout { }` block: a module path is resolved below the project root, so a
 *   directory around it would quietly be prepended to the answer.
 */
@ExperimentalKatachiApi
public fun LayoutScope.expandModulePath(
    modulePath: String,
    block: LayoutDirectoryScope.() -> Unit,
): LayoutModule = moduleAware().expandModulePath(modulePath, block)

/**
 * [expandModulePath], with a name for each `*` of [modulePath], in order: what
 * `"...".module(capture = ...)` does.
 *
 * ## Example 1: Write your own alias for a named `.module { }`
 * ```kt
 * import me.tbsten.katachi.ExperimentalKatachiApi
 * import me.tbsten.katachi.dsl.LayoutDirectoryScope
 * import me.tbsten.katachi.dsl.LayoutModule
 * import me.tbsten.katachi.dsl.LayoutScope
 * import me.tbsten.katachi.dsl.gradle.expandModulePath
 *
 * @OptIn(ExperimentalKatachiApi::class)
 * context(layoutScope: LayoutScope)
 * public fun featureModule(block: LayoutDirectoryScope.() -> Unit): LayoutModule =
 *     layoutScope.expandModulePath(":feature:*", listOf("feature"), block)
 * ```
 *
 * @param modulePath a Gradle module path, possibly holding `*` or `**`.
 * @param captures one name per `*` of [modulePath]; empty to name nothing.
 * @throws me.tbsten.katachi.dsl.KatachiInvalidIdentifierException when a name is not an identifier.
 * @throws me.tbsten.katachi.dsl.KatachiCaptureCountMismatchException when there is not exactly
 *   one name per `*`.
 * @throws me.tbsten.katachi.dsl.KatachiDuplicateCaptureException when a name is given twice.
 */
@ExperimentalKatachiApi
public fun LayoutScope.expandModulePath(
    modulePath: String,
    captures: List<String>,
    block: LayoutDirectoryScope.() -> Unit,
): LayoutModule = moduleAware().expandModulePath(modulePath, captures, block)

/**
 * The scope seen as what it knows, which is what this package is written against.
 *
 * Every scope katachi hands to a `layout { }` block implements [ModuleAwareLayoutScope], so
 * this holds; it is written as one function so that failing loudly when it does not has one
 * place.
 */
private fun LayoutScope.moduleAware(): ModuleAwareLayoutScope =
    this as? ModuleAwareLayoutScope
        ?: throw KatachiUnsupportedLayoutScopeException(this::class.simpleName ?: "unknown scope")
