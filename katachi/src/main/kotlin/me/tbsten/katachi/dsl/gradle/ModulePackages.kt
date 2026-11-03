@file:Suppress("DEPRECATION")

package me.tbsten.katachi.dsl.gradle

import me.tbsten.katachi.dsl.LayoutDirectory
import me.tbsten.katachi.dsl.LayoutDirectoryScope
import me.tbsten.katachi.dsl.LayoutFile
import me.tbsten.katachi.dsl.LayoutScope
import me.tbsten.katachi.dsl.internal.MODULE_PACKAGE_DEPRECATION
import me.tbsten.katachi.dsl.internal.markModulePackage

/**
 * Continues a `/` chain with the package directory of the module being evaluated.
 *
 * **Deprecated** since 0.3, removed in 0.5. See [ModulePackage] for what to write instead.
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
@Deprecated(message = MODULE_PACKAGE_DEPRECATION, level = DeprecationLevel.WARNING)
context(layoutScope: LayoutScope)
public operator fun String.div(child: ModulePackage): LayoutDirectory {
    val left = this
    val directory = child.packageDirectory()
    return with(layoutScope) { left / directory }
}

/**
 * `mainSourceSet / kotlin / modulePackage` continues below the module's package.
 *
 * **Deprecated** since 0.3, removed in 0.5. See [ModulePackage] for what to write instead.
 *
 * ## Example 1: Continue a source set into the module's package
 * ```kt
 * ":ui".module {
 *     mainSourceSet / kotlin / modulePackage / "component" / "*".ktFile()
 * }
 * ```
 */
@Deprecated(message = MODULE_PACKAGE_DEPRECATION, level = DeprecationLevel.WARNING)
context(layoutScope: LayoutScope)
public operator fun LayoutDirectory.div(child: ModulePackage): LayoutDirectory {
    val left = this
    val directory = child.packageDirectory()
    return with(layoutScope) { left / directory }
}

/**
 * Starts a `/` chain at the module's package directory.
 *
 * **Deprecated** since 0.3, removed in 0.5. See [ModulePackage] for what to write instead.
 *
 * ## Example 1: Continue below the module's package with a plain key
 * ```kt
 * ":core:domain".module {
 *     mainSourceSet / kotlin / modulePackage / "useCase" / "*UseCase".ktFile()
 * }
 * ```
 */
@Deprecated(message = MODULE_PACKAGE_DEPRECATION, level = DeprecationLevel.WARNING)
context(layoutScope: LayoutScope)
public operator fun ModulePackage.div(child: String): LayoutDirectory {
    val directory = packageDirectory()
    return with(layoutScope) { directory / child }
}

/**
 * Continues below the module's package directory with a directory block.
 *
 * **Deprecated** since 0.3, removed in 0.5. See [ModulePackage] for what to write instead.
 *
 * ## Example 1: Continue below the module's package with a directory value
 * ```kt
 * ":ui".module {
 *     val theme = "theme" { "AppTheme".ktFile() }
 *     mainSourceSet / kotlin / modulePackage / theme
 * }
 * ```
 */
@Deprecated(message = MODULE_PACKAGE_DEPRECATION, level = DeprecationLevel.WARNING)
context(layoutScope: LayoutScope)
public operator fun ModulePackage.div(child: LayoutDirectory): LayoutDirectory {
    val directory = packageDirectory()
    return with(layoutScope) { directory / child }
}

/**
 * `modulePackage / "*UseCase".ktFile()` puts the file in the module's package.
 *
 * **Deprecated** since 0.3, removed in 0.5. See [ModulePackage] for what to write instead.
 *
 * ## Example 1: Place a file directly in the module's package
 * ```kt
 * ":core:domain".module {
 *     modulePackage / "*UseCase".ktFile()
 * }
 * ```
 */
@Deprecated(message = MODULE_PACKAGE_DEPRECATION, level = DeprecationLevel.WARNING)
context(layoutScope: LayoutScope)
public operator fun ModulePackage.div(child: LayoutFile): LayoutFile {
    val directory = packageDirectory()
    return with(layoutScope) { directory / child }
}

/**
 * Opens a block at the module's package directory: `modulePackage { }` is the nested
 * spelling of `modulePackage / ...`.
 *
 * **Deprecated** since 0.3, removed in 0.5. See [ModulePackage] for what to write instead.
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
@Deprecated(message = MODULE_PACKAGE_DEPRECATION, level = DeprecationLevel.WARNING)
context(layoutScope: LayoutScope)
public operator fun ModulePackage.invoke(block: LayoutDirectoryScope.() -> Unit): LayoutDirectory {
    val directory = packageDirectory()
    return with(layoutScope) { directory(block) }
}

/**
 * The package directory of the module this block is being evaluated for, declared.
 *
 * It is declared here rather than handed back as a string so that the levels it produced can be
 * marked as the strategy's doing. Documentation shows a module package as a placeholder instead
 * of as `com/example/core/domain`, and once the levels are ordinary directory nodes nothing
 * tells them apart from levels the user wrote.
 *
 * @throws KatachiModulePackageException outside a module block, where there is no module to
 *   derive a package from.
 */
context(layoutScope: LayoutScope)
private fun ModulePackage.packageDirectory(): LayoutDirectory {
    val directory = resolveFor(layoutScope.currentModulePath)
    return with(layoutScope) { directory { } }.markModulePackage()
}
