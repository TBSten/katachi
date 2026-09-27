package me.tbsten.katachi.test.check.baseline

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.check.KatachiArchitectureAssertionError
import me.tbsten.katachi.check.KatachiBaselineCheckConflictException
import me.tbsten.katachi.check.KatachiBaselineNotFoundException
import me.tbsten.katachi.check.Severity
import me.tbsten.katachi.check.StaleBaselineEntry
import me.tbsten.katachi.check.UncheckedCheck
import me.tbsten.katachi.check.UnsatisfiedFileConstraint
import me.tbsten.katachi.check.Violation
import me.tbsten.katachi.check.internal.BaselineKey
import me.tbsten.katachi.check.internal.BaselineLedger
import me.tbsten.katachi.check.internal.BaselineRuns
import me.tbsten.katachi.check.internal.assertWith
import me.tbsten.katachi.check.internal.renderBaseline
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.Role
import me.tbsten.katachi.dsl.baselineFile
import me.tbsten.katachi.processor.ArchitectureProcessor
import me.tbsten.katachi.test.check.architectureOf
import me.tbsten.katachi.test.check.repositoryOf
import kotlin.random.Random

/*
 * The model of BaselineOperationSequenceSpec, widened to what a real project mixes: two check
 * classes and the layout check, violations keyed by path and by declaration, warnings, runs of
 * only some checks, runs where a check throws, and one check class split over two assert calls.
 */

private enum class SeenKind { Todo, Unsatisfied, Warning }

/** A violation "the project" has, reported by check [check] (0 or 1). */
private data class Seen(val check: Int, val kind: SeenKind, val path: String, val name: String, val line: Int)

private val constraintRole: Role = architectureOf { "domain".group { "UseCase" { } } }.allRoles.single()
private val checkNames = listOf(SCRIPTED, OTHER_SCRIPTED)
private val sourcePaths = listOf("a.kt", "b.kt", "c/d.kt")
private val layoutFiles = listOf("x.md", "y.md", "z.md")

private sealed interface Step {
    data class Add(val found: Seen) : Step
    data class Fix(val index: Int) : Step
    data class Shift(val index: Int, val line: Int) : Step
    data class Touch(val file: String) : Step
    data class Run(val mode: String, val checks: Set<Int>, val broken: Boolean) : Step
    data class Split(val seed: Int) : Step
}

private fun randomStep(random: Random): Step = when (random.nextInt(10)) {
    0, 1, 2 -> Step.Add(
        Seen(
            check = random.nextInt(2),
            kind = SeenKind.entries.random(random),
            path = sourcePaths.random(random),
            name = listOf("Helper", "Other").random(random),
            line = random.nextInt(1, 50),
        ),
    )
    3 -> Step.Fix(random.nextInt(100))
    4 -> Step.Shift(random.nextInt(100), random.nextInt(1, 50))
    5 -> Step.Touch(layoutFiles.random(random))
    6 -> Step.Split(random.nextInt())
    else -> Step.Run(
        mode = listOf("update", "prune", "normal", "normal").random(random),
        checks = listOf(setOf(0), setOf(1), setOf(0, 1)).random(random),
        broken = random.nextInt(6) == 0,
    )
}

private fun Seen.violation(): Violation = when (kind) {
    SeenKind.Todo -> ForeignViolation(path, label = "TodoRule")
    SeenKind.Warning -> ForeignViolation(path, label = "WarnRule", severity = Severity.Warning)
    SeenKind.Unsatisfied -> UnsatisfiedFileConstraint(
        path = path,
        declaration = name,
        line = line,
        role = constraintRole,
        constraintName = "is public",
        layoutPath = null,
        declaredAt = DeclarationSite("UseCaseRole.kt", 12),
    )
}

private fun Seen.key(): BaselineKey? = when (kind) {
    SeenKind.Todo -> BaselineKey(check = checkNames[check], rule = "TodoRule", path = path)
    SeenKind.Unsatisfied -> BaselineKey(
        check = checkNames[check],
        rule = "UnsatisfiedFileConstraint",
        path = path,
        role = constraintRole.qualifiedName,
        constraint = "is public",
        declaration = name,
    )
    SeenKind.Warning -> null
}

private fun Seen.describe(): String = "[${violation().label}] $path"

private fun layoutKey(file: String) = BaselineKey(check = LAYOUT, rule = "UnexpectedFile", path = file)

private sealed interface Verdict {
    object Green : Verdict { override fun toString() = "Green" }
    object NotFound : Verdict { override fun toString() = "NotFound" }
    data class Failed(val reported: List<String>) : Verdict
}

/** Everything that "the project" is at one moment. */
private class Project {
    val found = mutableListOf<Seen>()
    val files = sortedSetOf<String>()

    /** Every item a run of [checks] sees: its description, and its key when it has one. */
    fun items(checks: Set<Int>): List<Pair<String, BaselineKey?>> =
        files.map { "[UnexpectedFile] $it" to layoutKey(it) } +
            found.filter { it.check in checks }.map { it.describe() to it.key() }
}

private fun counts(items: List<Pair<String, BaselineKey?>>): Map<BaselineKey, Int> =
    items.mapNotNull { it.second }.groupingBy { it }.eachCount()

private fun ranNames(checks: Set<Int>): Set<String> = checks.map { checkNames[it] }.toSet() + LAYOUT

/** The model's verdict for an ordinary run of [checks] against [ledger]. */
private fun expectedRun(ledger: Map<BaselineKey, Int>, project: Project, checks: Set<Int>, broken: Boolean): Verdict {
    val items = project.items(checks)
    val found = counts(items)
    val errors = mutableListOf<String>()
    val reported = mutableListOf<String>()
    for ((description, key) in items) {
        if (key == null) {
            reported += description
        } else if (found.getValue(key) > (ledger[key] ?: 0)) {
            reported += description
            errors += description
        }
    }
    if (!broken) {
        for ((key, allowed) in ledger) {
            val count = found[key] ?: 0
            if (key.check in ranNames(checks) && count < allowed) {
                val stale = "[StaleBaselineEntry] ${key.path} ${key.rule} $allowed $count"
                reported += stale
                errors += stale
            }
        }
    }
    return if (errors.isEmpty() && !broken) Verdict.Green else Verdict.Failed(reported.sorted())
}

private fun describe(violations: List<Violation>): List<String> = violations
    .filterNot { it is UncheckedCheck }
    .map { if (it is StaleBaselineEntry) "[StaleBaselineEntry] ${it.path} ${it.rule} ${it.allowed} ${it.found}" else "[${it.label}] ${it.path}" }
    .sorted()

private val definition: Architecture = architectureOf {
    baseline = baselineFile()
    "Readme" { layout { "README.md".file() } }
    "Baseline" { layout { "katachi-baseline.json".file() } }
}

class BaselineMultiCheckSequenceSpec : FreeSpec({
    "複数の check・キーの型・Warning・投げる check・設定違いの分割を混ぜても、モデルと結果が一致する" {
        repeat(300) { seed ->
            val random = Random(seed)
            val steps = List(14) { randomStep(random) }
            val store = MemoryBaselineStore()
            val project = Project()
            var ledger: Map<BaselineKey, Int>? = null

            fun checksFor(ran: Set<Int>, broken: Boolean): List<ArchitectureProcessor<Unit, List<Violation>>> = buildList {
                if (0 in ran) add(ScriptedCheck(project.found.filter { it.check == 0 }.map { it.violation() }))
                if (1 in ran) add(OtherScriptedCheck(project.found.filter { it.check == 1 }.map { it.violation() }))
                if (broken) add(ThrowingScriptedCheck())
            }

            fun tree() = repositoryOf {
                "README.md"()
                for (file in project.files) file()
                // Once written, the baseline is a file of the project; it is declared above.
                if (store.files.containsKey(BASELINE_FILE)) "katachi-baseline.json"()
            }

            fun actual(
                ran: Set<Int>,
                broken: Boolean = false,
                update: Boolean = false,
                prune: Boolean = false,
                checks: List<ArchitectureProcessor<Unit, List<Violation>>> = checksFor(ran, broken),
                runs: BaselineRuns = BaselineRuns(),
            ): Verdict = try {
                definition.assertWith(tree(), checks, 10, environmentOf(store, update = update, prune = prune, runs = runs))
                Verdict.Green
            } catch (failure: KatachiArchitectureAssertionError) {
                Verdict.Failed(describe(failure.violations))
            } catch (_: KatachiBaselineNotFoundException) {
                Verdict.NotFound
            }

            for ((index, step) in steps.withIndex()) {
                withClue("seed=$seed step=$index steps=$steps found=${project.found} files=${project.files} ledger=$ledger") {
                    when (step) {
                        is Step.Add -> project.found += step.found
                        is Step.Fix -> if (project.found.isNotEmpty()) project.found.removeAt(step.index % project.found.size)
                        // A line moving never changes a key.
                        is Step.Shift -> if (project.found.isNotEmpty()) {
                            val at = step.index % project.found.size
                            project.found[at] = project.found[at].copy(line = step.line)
                        }
                        is Step.Touch -> if (!project.files.add(step.file)) project.files.remove(step.file)
                        is Step.Split -> {
                            // One check class, two assert calls, two halves of what it reports.
                            val mine = project.found.filter { it.check == 0 }.shuffled(Random(step.seed))
                            val first = mine.take(mine.size / 2)
                            val second = mine.drop(mine.size / 2)
                            val runs = BaselineRuns()
                            actual(setOf(0), checks = listOf(ScriptedCheck(first.map { it.violation() })), runs = runs)
                            val same = counts(first.map { "" to it.key() }) == counts(second.map { "" to it.key() })
                            val secondRun = { actual(setOf(0), checks = listOf(ScriptedCheck(second.map { it.violation() })), runs = runs) }
                            if (same) secondRun() else shouldThrow<KatachiBaselineCheckConflictException> { secondRun() }
                        }
                        is Step.Run -> {
                            val known = ledger
                            val items = project.items(step.checks)
                            when {
                                step.mode == "normal" -> actual(step.checks, step.broken) shouldBe
                                    (if (known == null) Verdict.NotFound else expectedRun(known, project, step.checks, step.broken))
                                step.mode == "prune" && known == null -> actual(step.checks, step.broken, prune = true) shouldBe Verdict.NotFound
                                step.broken -> {
                                    // A partial run writes nothing and reports everything it saw.
                                    val before = store.files[BASELINE_FILE]
                                    actual(step.checks, broken = true, update = step.mode == "update", prune = step.mode == "prune") shouldBe
                                        Verdict.Failed(items.map { it.first }.sorted())
                                    store.files[BASELINE_FILE] shouldBe before
                                }
                                step.mode == "update" -> {
                                    val ran = ranNames(step.checks)
                                    val updated = (known ?: emptyMap()).filterKeys { it.check !in ran } + counts(items)
                                    actual(step.checks, update = true) shouldBe Verdict.Green
                                    ledger = updated
                                    store.files[BASELINE_FILE] shouldBe renderBaseline(BaselineLedger(updated))
                                    // Idempotent, and green for the same checks right after.
                                    actual(step.checks, update = true) shouldBe Verdict.Green
                                    store.files[BASELINE_FILE] shouldBe renderBaseline(BaselineLedger(updated))
                                    actual(step.checks) shouldBe Verdict.Green
                                }
                                else -> {
                                    val ran = ranNames(step.checks)
                                    val found = counts(items)
                                    val pruned = known!!
                                        .mapValues { (key, allowed) -> if (key.check in ran) minOf(allowed, found[key] ?: 0) else allowed }
                                        .filterValues { it > 0 }
                                    actual(step.checks, prune = true) shouldBe expectedRun(pruned, project, step.checks, broken = false)
                                    ledger = pruned
                                    store.files[BASELINE_FILE] shouldBe renderBaseline(BaselineLedger(pruned))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
})
