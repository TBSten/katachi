package me.tbsten.katachi.check

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.check.internal.assertNoErrors
import me.tbsten.katachi.check.internal.layoutWarningsOf
import me.tbsten.katachi.internal.runProcessorCatching
import me.tbsten.katachi.processor.ArchitectureProcessContext
import me.tbsten.katachi.processor.ArchitectureProcessorNoArg
import me.tbsten.katachi.processor.internal.projectWalk

/**
 * The `layout { }` check, written as a processor: files the project has that no role allows,
 * files a role requires that the project does not have, and the paths the walk could not read.
 *
 * **It returns violations rather than throwing them**, which is what the `Check` suffix means
 * here — a processor whose result is a `List<Violation>` carries that suffix, and one that does
 * something else is named after the something else (`GenerateDocs`). Throwing on the spot would
 * make whichever check ran first the end of the run, and there is no reason for the first one
 * to win: a reader wants one report holding everything that is off, not the first finding plus
 * another run to learn the second. Turning a result into a test failure is `assert()`'s job,
 * and `assert()` is the only entry point that throws.
 *
 * "Returns" means [assertNoErrors] at the end of the body: warnings only is a
 * `success`, and anything that fails the check is a `failure` carrying a
 * [KatachiArchitectureAssertionError] that holds every violation found, warnings included.
 * Whatever else goes wrong -- no project root, a mistake in the definition -- is a `failure`
 * too, and is not thrown out of `process`.
 *
 * Being a processor also means it shares the one walk of the project with everything else that
 * reads the same [ArchitectureProcessContext]: asking this for violations and asking
 * [ArchitectureProcessContext.filesOf] for a role's files costs one traversal, not two — and,
 * because it is the same traversal, the two answers cannot drift apart.
 *
 * It takes no settings. `maxViolations` belongs to the report rather than to the check, which
 * always looks at everything.
 *
 * Not everything it returns comes from that walk, though.
 * [ArchitectureProcessContext.declaredEntries] costs no walk of its own — it is the `layout { }`
 * blocks read from the declarations alone — and is where declaration-only Warnings live: a path
 * two roles both claim outright ([me.tbsten.katachi.check.AmbiguousLayout]), and a role living in
 * more than one place without saying which files belong in which
 * ([me.tbsten.katachi.check.MissingDescription]). It is taken from the evaluation the walk
 * already made rather than made again, so a role's `layout { }` block runs once per `assert()`.
 * The exception is a role with a wildcard module key: read from the declarations, that key
 * stays one pattern, while the walk expands it to the modules that exist, so such a block runs
 * once for each reading — on top of the once per module it expands to.
 *
 * The third Warning goes the other way and is why both halves are handed over together: the
 * same walk also holds the roles whose *different* patterns turned out to select the same real
 * files -- an overlap only katachi's own check can see, read through a door that stays internal
 * rather than on the public context. It is the same [me.tbsten.katachi.check.AmbiguousLayout]
 * the declarations produce, so the two are merged where each can see the other rather than
 * concatenated — a pair of roles named by both must be one block, not two.
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
 * `assert()` runs this check without being asked for it.
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
 * It is not registered by default: the test already runs it through `assert()`, and a key of its
 * own is only needed to run the check alone from the command line.
 */
@ExperimentalKatachiApi
public class LayoutCheck : ArchitectureProcessorNoArg<List<Violation>> {
    override fun process(context: ArchitectureProcessContext<Unit>): Result<List<Violation>> =
        runProcessorCatching {
            val walk = context.projectWalk
            (
                walk.layoutViolations +
                    layoutWarningsOf(context.declaredEntries, walk.layoutFileOverlaps)
                ).assertNoErrors(walk.projectRoot)
        }
}
