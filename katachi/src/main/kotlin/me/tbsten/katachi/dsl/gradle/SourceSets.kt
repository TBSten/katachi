package me.tbsten.katachi.dsl.gradle

import me.tbsten.katachi.dsl.LayoutDirectory
import me.tbsten.katachi.dsl.LayoutScope

/**
 * Where a Gradle module keeps its sources, named as Gradle names it.
 *
 * Every declaration below is written through [LayoutScope]'s own directory key and nothing
 * else — `"main".sourceSet` *is* `"src/main" { }` — which is why a source set and a plain
 * directory cannot come apart, and why a project can write its own `androidTestSourceSet`
 * the same way.
 */

/**
 * The source set directory `src/<this>`, and nothing more than that.
 *
 * `"commonMain".sourceSet` is `src/commonMain`. `kotlin` is not implied — write [kotlin]
 * for it — and neither `main` nor `commonMain` is chosen for you, because that would mean
 * guessing at the kind of project this is.
 *
 * ```kt
 * val commonMain = "commonMain".sourceSet   // if writing it every time grates
 * ```
 *
 * ## Example 1: Name a source set of your own
 * ```kt
 * import me.tbsten.katachi.dsl.gradle.*
 * import me.tbsten.katachi.dsl.kotlin.ktFile
 *
 * ":testing".module {
 *     "commonMain".sourceSet / kotlin / modulePackage / "Fake*".ktFile()
 * }
 * ```
 */
context(layoutScope: LayoutScope)
public val String.sourceSet: LayoutDirectory
    get() {
        val name = this
        return with(layoutScope) { "src/$name" { } }
    }

/**
 * `src/main`, the same as `"main".sourceSet`.
 *
 * ## Example 1: Place files directly under src/main
 * ```kt
 * ":app".module {
 *     mainSourceSet {
 *         "AndroidManifest.xml".file()
 *         "res".ignore()
 *     }
 * }
 * ```
 */
context(layoutScope: LayoutScope)
public val mainSourceSet: LayoutDirectory get() = "main".sourceSet

/**
 * `src/test`, the same as `"test".sourceSet`.
 *
 * ## Example 1: Place test code under src/test
 * ```kt
 * ":architecture-test".module {
 *     testSourceSet / kotlin / "com/example/sample" {
 *         "*Spec".ktFile()
 *         "*Test".ktFile()
 *     }
 * }
 * ```
 */
context(layoutScope: LayoutScope)
public val testSourceSet: LayoutDirectory get() = "test".sourceSet

/**
 * The `kotlin` directory, the same as `"kotlin" { }`.
 *
 * A source set does not imply it, so it is written out on both spellings of a path:
 * `mainSourceSet / kotlin / ...` and `mainSourceSet { kotlin { } }`.
 *
 * ## Example 1: Reach the kotlin directory below a source set
 * ```kt
 * ":core:domain".module {
 *     mainSourceSet / kotlin / "Foo".ktFile()
 * }
 * ```
 */
context(layoutScope: LayoutScope)
public val kotlin: LayoutDirectory get() = with(layoutScope) { "kotlin" { } }
