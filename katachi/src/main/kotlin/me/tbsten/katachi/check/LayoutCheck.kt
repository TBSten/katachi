package me.tbsten.katachi.check

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.processor.ArchitectureProcessor
import me.tbsten.katachi.processor.ProjectModel
import me.tbsten.katachi.scan.Violation
import me.tbsten.katachi.scan.layoutWarningsOf

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
 * Not everything it returns comes from that walk, though. [ProjectModel.declaredEntries] costs
 * no walk of its own — it flattens the same `layout { }` blocks a second time, from the
 * declarations alone — and is where declaration-only Warnings live: a path two roles both claim
 * outright ([me.tbsten.katachi.scan.AmbiguousLayout]), and a role living in more than one place
 * without saying which files belong in which
 * ([me.tbsten.katachi.scan.MissingDescription]). Reading it is a second
 * evaluation of every `layout { }` block, so a block with a side effect of its own runs twice
 * per `assert()` — the same thing a wildcard module key already does once per module it expands
 * to.
 *
 * The third Warning goes the other way and is why both halves are handed over together:
 * [ProjectModel.layoutFileOverlaps] holds the roles whose *different* patterns turned out to
 * select the same real files, which only the walk can know. It is the same
 * [me.tbsten.katachi.scan.AmbiguousLayout] the declarations produce, so the two are merged
 * where each can see the other rather than concatenated — a pair of roles named by both must
 * be one block, not two.
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
    // Reading `layoutViolations` is what starts the walk; see [ProjectModel.layoutViolations]
    // for why a processor is handed it through a door only katachi's own check can open.
    // `declaredEntries` costs no walk — see this class's own KDoc for why it is read here too.
    override fun process(model: ProjectModel): List<Violation> =
        model.layoutViolations + layoutWarningsOf(model.declaredEntries, model.layoutFileOverlaps)
}
