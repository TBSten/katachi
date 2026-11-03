package me.tbsten.katachi.dsl.kotlin

import me.tbsten.katachi.dsl.LayoutFile
import me.tbsten.katachi.dsl.LayoutScope

/**
 * Declares a file with `.kt` appended: `"*UseCase".ktFile()` is `*UseCase.kt`.
 *
 * The same as `"*UseCase.kt".file()`, and produces the same declaration.
 *
 * ## Example 1: Declare a Kotlin file by name
 * ```kt
 * import me.tbsten.katachi.dsl.gradle.*
 * import me.tbsten.katachi.dsl.kotlin.ktFile
 *
 * "app" / mainSourceSet / kotlin / "com/example/sample" {
 *     "MainActivity".ktFile()
 *     "MainApplication".ktFile()
 * }
 * ```
 */
context(layoutScope: LayoutScope)
public fun String.ktFile(): LayoutFile {
    val name = this
    return with(layoutScope) { "$name.kt".file() }
}

/**
 * Declares a file with `.kts` appended: `"build.gradle".ktsFile()` is `build.gradle.kts`.
 *
 * The same as `"build.gradle.kts".file()`, and produces the same declaration.
 *
 * ## Example 1: Declare a settings script
 * ```kt
 * import me.tbsten.katachi.dsl.gradle.*
 * import me.tbsten.katachi.dsl.kotlin.ktsFile
 *
 * "settings.gradle".ktsFile()
 * ```
 */
context(layoutScope: LayoutScope)
public fun String.ktsFile(): LayoutFile {
    val name = this
    return with(layoutScope) { "$name.kts".file() }
}
