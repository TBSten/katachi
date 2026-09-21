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
 * ```kotlin
 * val commonMain = "commonMain".sourceSet   // if writing it every time grates
 * ```
 */
context(layoutScope: LayoutScope)
public val String.sourceSet: LayoutDirectory
    get() {
        val name = this
        return with(layoutScope) { "src/$name" { } }
    }

/** `src/main`, the same as `"main".sourceSet`. */
context(layoutScope: LayoutScope)
public val mainSourceSet: LayoutDirectory get() = "main".sourceSet

/** `src/test`, the same as `"test".sourceSet`. */
context(layoutScope: LayoutScope)
public val testSourceSet: LayoutDirectory get() = "test".sourceSet

/**
 * The `kotlin` directory, the same as `"kotlin" { }`.
 *
 * A source set does not imply it, so it is written out on both spellings of a path:
 * `mainSourceSet / kotlin / ...` and `mainSourceSet { kotlin { } }`.
 */
context(layoutScope: LayoutScope)
public val kotlin: LayoutDirectory get() = with(layoutScope) { "kotlin" { } }
