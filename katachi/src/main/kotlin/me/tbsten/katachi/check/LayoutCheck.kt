package me.tbsten.katachi.check

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.check.internal.assertNoErrors
import me.tbsten.katachi.check.internal.layoutWarningsOf
import me.tbsten.katachi.internal.runProcessorCatching
import me.tbsten.katachi.processor.ArchitectureProcessContext
import me.tbsten.katachi.processor.ArchitectureProcessNoArgContext
import me.tbsten.katachi.processor.ArchitectureProcessorNoArg
import me.tbsten.katachi.processor.internal.projectWalk

/**
 * The `layout { }` check: files no role allows, files a role requires that are missing, and paths
 * that could not be read.
 *
 * It returns violations rather than throwing them, so every check of a run ends up in one report;
 * turning the result into a test failure is `assert()`'s job, and `assert()` runs this check
 * without being asked for it. Warnings only is a `success`; anything that fails the check is a
 * `failure` carrying a [KatachiArchitectureAssertionError] that holds every violation, warnings
 * included.
 *
 * It shares the one walk of the project with everything else reading the same
 * [ArchitectureProcessContext], so its answer and [ArchitectureProcessContext.filesOf] always
 * agree. Its warning is [AmbiguousLayout]. It takes no settings:
 * `maxViolations` belongs to the report, and the check always looks at everything. It is not
 * registered with the Gradle plugin by default; register it only to run it alone from the command
 * line.
 *
 * ## Example 1: check the project from a test
 * ```kt
 * import me.tbsten.katachi.check.assert
 *
 * class ProjectArchitectureTest {
 *     @Test
 *     fun `the project matches its declaration`() = projectArchitecture.assert()
 * }
 * ```
 *
 * ## Example 2: accept what is already there and fail only on something new
 * ```kt
 * import me.tbsten.katachi.check.KatachiArchitectureAssertionError
 * import me.tbsten.katachi.check.LayoutCheck
 * import me.tbsten.katachi.processor.process
 *
 * val baseline = setOf("legacy/Untouched.kt")
 * val found = projectArchitecture.process(LayoutCheck()).fold(
 *     onSuccess = { it },
 *     onFailure = { (it as? KatachiArchitectureAssertionError)?.violations ?: throw it },
 * )
 * found.map { it.path }.filterNot { it in baseline } shouldBe emptyList()
 * ```
 *
 * ## Example 3: run it from the command line
 * ```kts
 * // architecture-test/build.gradle.kts
 * katachi {
 *     processors {
 *         register("layout", "me.tbsten.katachi.check.LayoutCheck")
 *     }
 * }
 * ```
 * ```sh
 * ./gradlew :architecture-test:katachiLayout
 * ```
 *
 * @constructor Takes nothing: the check has no settings.
 */
@ExperimentalKatachiApi
public class LayoutCheck : ArchitectureProcessorNoArg<List<Violation>> {
    override fun process(context: ArchitectureProcessNoArgContext): Result<List<Violation>> =
        runProcessorCatching {
            val walk = context.projectWalk
            (
                walk.layoutViolations +
                    layoutWarningsOf(context.declaredEntries, walk.layoutFileOverlaps)
                ).assertNoErrors(walk.projectRoot)
        }
}
