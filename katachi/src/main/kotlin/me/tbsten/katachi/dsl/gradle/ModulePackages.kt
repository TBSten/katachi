package me.tbsten.katachi.dsl.gradle

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

/**
 * Continues a `/` chain with the package directory of the module being evaluated.
 *
 * ## Example 1: Continue a raw path string into the module's package
 * ```kt
 * import me.tbsten.katachi.dsl.gradle.*
 * import me.tbsten.katachi.dsl.kotlin.ktFile
 *
 * ":core:data".module {
 *     "src/main/kotlin" / modulePackage / "*".ktFile()
 * }
 * ```
 */
context(layoutScope: LayoutScope)
public operator fun String.div(child: ModulePackage): LayoutDirectory {
    val left = this
    val directory = child.packageDirectory()
    return with(layoutScope) { left / directory }
}

/**
 * `mainSourceSet / kotlin / modulePackage` continues below the module's package.
 *
 * ## Example 1: Continue a source set into the module's package
 * ```kt
 * ":ui".module {
 *     mainSourceSet / kotlin / modulePackage / "component" / "*".ktFile()
 * }
 * ```
 */
context(layoutScope: LayoutScope)
public operator fun LayoutDirectory.div(child: ModulePackage): LayoutDirectory {
    val left = this
    val directory = child.packageDirectory()
    return with(layoutScope) { left / directory }
}

/**
 * Starts a `/` chain at the module's package directory.
 *
 * ## Example 1: Continue below the module's package with a plain key
 * ```kt
 * ":core:domain".module {
 *     mainSourceSet / kotlin / modulePackage / "useCase" / "*UseCase".ktFile()
 * }
 * ```
 */
context(layoutScope: LayoutScope)
public operator fun ModulePackage.div(child: String): LayoutDirectory {
    val directory = packageDirectory()
    return with(layoutScope) { directory / child }
}

/**
 * Continues below the module's package directory with a directory block.
 *
 * ## Example 1: Continue below the module's package with a directory value
 * ```kt
 * ":ui".module {
 *     val theme = "theme" { "AppTheme".ktFile() }
 *     mainSourceSet / kotlin / modulePackage / theme
 * }
 * ```
 */
context(layoutScope: LayoutScope)
public operator fun ModulePackage.div(child: LayoutDirectory): LayoutDirectory {
    val directory = packageDirectory()
    return with(layoutScope) { directory / child }
}

/**
 * `modulePackage / "*UseCase".ktFile()` puts the file in the module's package.
 *
 * ## Example 1: Place a file directly in the module's package
 * ```kt
 * ":core:domain".module {
 *     modulePackage / "*UseCase".ktFile()
 * }
 * ```
 */
context(layoutScope: LayoutScope)
public operator fun ModulePackage.div(child: LayoutFile): LayoutFile {
    val directory = packageDirectory()
    return with(layoutScope) { directory / child }
}

/**
 * Opens a block at the module's package directory: `modulePackage { }` is the nested
 * spelling of `modulePackage / ...`.
 *
 * ## Example 1: Open a block at the module's package directory
 * ```kt
 * ":data".module {
 *     mainSourceSet / kotlin / modulePackage {
 *         description = "The interface, which the caller depends on"
 *         "user" { "*Repository".ktFile() }
 *         "settings" { "*Repository".ktFile() }
 *     }
 * }
 * ```
 */
context(layoutScope: LayoutScope)
public operator fun ModulePackage.invoke(block: LayoutDirectoryScope.() -> Unit): LayoutDirectory {
    val directory = packageDirectory()
    return with(layoutScope) { directory(block) }
}

/**
 * The package directory of the module this block is being evaluated for.
 *
 * @throws KatachiModulePackageException outside a module block, where there is no module to
 *   derive a package from.
 */
context(layoutScope: LayoutScope)
private fun ModulePackage.packageDirectory(): String = resolveFor(layoutScope.currentModulePath)
