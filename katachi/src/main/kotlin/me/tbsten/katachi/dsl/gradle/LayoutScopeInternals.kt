package me.tbsten.katachi.dsl.gradle

import me.tbsten.katachi.InternalKatachiApi
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
 * project's own as much as katachi's. They are opt-in rather than `internal` for the same
 * reason. A project whose build is unusual enough to need its own `.module { }` should be
 * able to write one; it is not a supported surface, and the opt-in is what says so.
 */

/**
 * The module this scope is being evaluated for, as katachi prints it (`":feature:home"`),
 * or `null` directly under `layout { }` and inside a plain directory block.
 */
@InternalKatachiApi
public val LayoutScope.currentModulePath: String?
    get() = moduleAware().currentModulePath

/**
 * What the module path's wildcards captured for the module being evaluated, or `null`
 * outside a module block, where there is no module path to have captured anything.
 *
 * See [wildcards], which is this with the error message.
 */
@InternalKatachiApi
public val LayoutScope.currentWildcards: List<String>?
    get() = moduleAware().currentWildcards

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
 * @param modulePath a Gradle module path, possibly holding `*` or `**`.
 * @throws me.tbsten.katachi.check.KatachiGlobSyntaxException when the module path cannot be read.
 * @throws me.tbsten.katachi.dsl.KatachiModuleOutsideLayoutRootException when this scope is not
 *   the root of a `layout { }` block: a module path is resolved below the project root, so a
 *   directory around it would quietly be prepended to the answer.
 */
@InternalKatachiApi
public fun LayoutScope.expandModulePath(
    modulePath: String,
    block: LayoutDirectoryScope.() -> Unit,
): LayoutModule = moduleAware().expandModulePath(modulePath, block)

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
