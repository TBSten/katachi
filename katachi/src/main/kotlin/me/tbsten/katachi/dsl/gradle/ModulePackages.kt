// `resolveFor` is katachi's own: it turns the user's strategy into a directory for the
// module a block is being evaluated for, which is state only the scope holds.
@file:OptIn(InternalKatachiApi::class)

package me.tbsten.katachi.dsl.gradle

import me.tbsten.katachi.dsl.InternalKatachiApi
import me.tbsten.katachi.dsl.LayoutDirectory
import me.tbsten.katachi.dsl.LayoutDirectoryScope
import me.tbsten.katachi.dsl.LayoutFile
import me.tbsten.katachi.dsl.LayoutScope

/**
 * Continues a `/` chain, or opens a block, at the package directory of the module being
 * evaluated.
 *
 * A [ModulePackage] is a strategy rather than a path, so every one of these first asks it
 * for the directory of the module this block belongs to and then carries on with the plain
 * directory key that came back. `modulePackage / "user"` and `"com/example/data" / "user"`
 * declare the same thing; the first says *why*.
 */

/** Continues a `/` chain with the package directory of the module being evaluated. */
context(layoutScope: LayoutScope)
public operator fun String.div(child: ModulePackage): LayoutDirectory {
    val left = this
    val directory = child.packageDirectory()
    return with(layoutScope) { left / directory }
}

/** `mainSourceSet / kotlin / modulePackage` continues below the module's package. */
context(layoutScope: LayoutScope)
public operator fun LayoutDirectory.div(child: ModulePackage): LayoutDirectory {
    val left = this
    val directory = child.packageDirectory()
    return with(layoutScope) { left / directory }
}

/** Starts a `/` chain at the module's package directory. */
context(layoutScope: LayoutScope)
public operator fun ModulePackage.div(child: String): LayoutDirectory {
    val directory = packageDirectory()
    return with(layoutScope) { directory / child }
}

/** Continues below the module's package directory with a directory block. */
context(layoutScope: LayoutScope)
public operator fun ModulePackage.div(child: LayoutDirectory): LayoutDirectory {
    val directory = packageDirectory()
    return with(layoutScope) { directory / child }
}

/** `modulePackage / "*UseCase".ktFile()` puts the file in the module's package. */
context(layoutScope: LayoutScope)
public operator fun ModulePackage.div(child: LayoutFile): LayoutFile {
    val directory = packageDirectory()
    return with(layoutScope) { directory / child }
}

/**
 * Opens a block at the module's package directory: `modulePackage { }` is the nested
 * spelling of `modulePackage / ...`.
 */
context(layoutScope: LayoutScope)
public operator fun ModulePackage.invoke(block: LayoutDirectoryScope.() -> Unit): LayoutDirectory {
    val directory = packageDirectory()
    return with(layoutScope) { directory(block) }
}

/**
 * The package directory of the module this block is being evaluated for.
 *
 * @throws me.tbsten.katachi.dsl.ModulePackageException outside a module block, where there
 *   is no module to derive a package from.
 */
context(layoutScope: LayoutScope)
private fun ModulePackage.packageDirectory(): String = resolveFor(layoutScope.currentModulePath)
