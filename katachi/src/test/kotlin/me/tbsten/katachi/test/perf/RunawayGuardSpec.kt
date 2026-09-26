package me.tbsten.katachi.test.perf

import io.kotest.core.Tag
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.measureTimedValue
import me.tbsten.katachi.check.Severity
import me.tbsten.katachi.check.internal.validate
import me.tbsten.katachi.test.synthetic.architecture
import me.tbsten.katachi.test.synthetic.generateProject
import me.tbsten.katachi.test.synthetic.inMemoryFileSystem

/**
 * Specs that measure time or scale. `-Dkotest.tags.exclude=Perf` (forwarded to the test JVM by
 * katachi/build.gradle.kts) or `KOTEST_TAGS='!Perf'` leaves them out of a local run.
 */
object Perf : Tag()

/**
 * Wall-clock time measured locally for one whole check of the 10k-file project below, cold (the
 * first call in a fresh test JVM). The measurements are in
 * `.local/research-performance/impl/benchmark/guard.md`.
 */
private val LOCAL_MEASURED = 1_700.milliseconds

/**
 * The limit is not a regression detector: it only catches a run that is off by an order of
 * magnitude (an accidental O(N²), a loop that never ends), the way konture guards its benchmark.
 * CI runners are slower and noisier than a laptop, hence the wider margin there.
 */
private val LIMIT: Duration = LOCAL_MEASURED * if (System.getenv("CI") != null) 15 else 5

class RunawayGuardSpec : FreeSpec({
    tags(Perf)

    // `timeout` alone stops a hang; `blockingTest` puts the body on its own thread so that a
    // non-suspending loop can be interrupted. The measured assertion below says by how much.
    "10k ファイル × ロール 20 × モジュール 100 のチェック全体が上限の時間に収まる".config(
        timeout = LIMIT * 2,
        blockingTest = true,
    ) {
        val project = generateProject(files = 10_000, roles = 20, modules = 100, seed = 5)
        val architecture = project.architecture()
        val fileSystem = project.inMemoryFileSystem()

        // What `assert()` does, through the entry that takes a file system: validate, then fail
        // on errors. `assert(fileSystem)` itself is internal to :katachi.
        val (violations, elapsed) = measureTimedValue { architecture.validate(fileSystem) }
        println("RunawayGuardSpec: ${elapsed.inWholeMilliseconds} ms (limit ${LIMIT.inWholeMilliseconds} ms)")

        elapsed shouldBeLessThan LIMIT
        // The tree has unknown directories on purpose, so errors are expected; check they are the
        // expected ones, so that the time is known to be spent on the real answer.
        violations.map { "[${it.label}] ${it.path}" }.sorted() shouldBe project.expectedViolationLabels
        violations.filter { it.severity != Severity.Error }.shouldBeEmpty()
    }
})
