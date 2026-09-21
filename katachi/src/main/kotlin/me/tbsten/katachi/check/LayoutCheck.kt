package me.tbsten.katachi.check

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.processor.ArchitectureProcessor
import me.tbsten.katachi.processor.ProjectModel
import me.tbsten.katachi.scan.Violation

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
 * Being a processor also means it shares the one walk of the project with everything else that
 * reads the same [ProjectModel]: asking this for violations and asking [ProjectModel.filesOf]
 * for a role's files costs one traversal, not two — and, because it is the same traversal,
 * the two answers cannot drift apart.
 *
 * It takes no settings. `maxViolations` belongs to the report rather than to the check, which
 * always looks at everything.
 *
 * ## Example 1: look at what the check found without failing the test
 * ```kt
 * val violations = projectArchitecture.process(LayoutCheck())
 * violations.map { it.path } shouldContain "notes.md"
 * ```
 *
 * ## Example 2: accept what is already there and fail only on something new
 * ```kt
 * val baseline = setOf("legacy/Untouched.kt")
 * val fresh = projectArchitecture.process(LayoutCheck()).filterNot { it.path in baseline }
 * fresh.map { it.path } shouldBe emptyList()
 * ```
 */
@ExperimentalKatachiApi
public class LayoutCheck : ArchitectureProcessor<List<Violation>> {
    // Reading this is what starts the walk; see [ProjectModel.layoutViolations] for why a
    // processor is handed it through a door only katachi's own check can open.
    override fun process(model: ProjectModel): List<Violation> = model.layoutViolations
}
