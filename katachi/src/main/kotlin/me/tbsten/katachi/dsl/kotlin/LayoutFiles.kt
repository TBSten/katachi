package me.tbsten.katachi.dsl.kotlin

import me.tbsten.katachi.dsl.LayoutFile
import me.tbsten.katachi.dsl.LayoutScope

/**
 * File spellings that are common enough to be worth a name, written on top of
 * [LayoutScope.file] and nothing else.
 *
 * These two are katachi's own worked example of a utility layer. Neither reaches inside the
 * DSL: each takes the scope as a context parameter and calls the core `file()` through it,
 * which is exactly what a project writes when it wants `"...".protoFile()` or
 * `"...".xmlFile()` of its own. Nothing here is a member of [LayoutScope], so nothing here
 * is katachi's privilege.
 */

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
 * ":app".module {
 *     mainSourceSet / kotlin / "com/example/sample" {
 *         "MainActivity".ktFile()
 *         "MainApplication".ktFile()
 *     }
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
 * ## Example 1: Declare a module's own build script
 * ```kt
 * import me.tbsten.katachi.dsl.gradle.*
 * import me.tbsten.katachi.dsl.kotlin.ktsFile
 *
 * ":".module {
 *     "settings.gradle".ktsFile()
 * }
 * ```
 */
context(layoutScope: LayoutScope)
public fun String.ktsFile(): LayoutFile {
    val name = this
    return with(layoutScope) { "$name.kts".file() }
}
