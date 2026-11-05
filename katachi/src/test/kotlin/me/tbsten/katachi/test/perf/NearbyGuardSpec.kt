package me.tbsten.katachi.test.perf

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.measureTimedValue
import me.tbsten.katachi.check.UnexpectedFile
import me.tbsten.katachi.check.validate
import me.tbsten.katachi.test.synthetic.architecture
import me.tbsten.katachi.test.synthetic.generateProject
import me.tbsten.katachi.test.synthetic.inMemoryFileSystem

/**
 * Wall-clock time measured locally for one whole check of the project below, cold (the first
 * call in a fresh test JVM): 1,000 unexpected files, each ranked against the 1,000 places the
 * ten module roles expand to over 100 modules.
 */
private val LOCAL_MEASURED = 2_500.milliseconds

/** The same margin as `RunawayGuardSpec`: only a run off by an order of magnitude is caught. */
private val LIMIT: Duration = LOCAL_MEASURED * if (System.getenv("CI") != null) 15 else 5

/**
 * Nearby locations are computed once per `[UnexpectedFile]`, over every place every role
 * declares; with wildcard places that is roles x modules candidates per file, and a naive
 * product of the two would be the first thing to blow up on a large project.
 */
class NearbyGuardSpec : FreeSpec({
    tags(Perf)

    "Unexpected なファイル 1,000 件 × ワイルドカードの Role 20 × モジュール 100 でも Nearby の計算で遅くならない".config(
        timeout = LIMIT * 2,
        blockingTest = true,
    ) {
        val project = generateProject(
            files = 5_000,
            roles = 20,
            modules = 100,
            seed = 9,
            unknownDirectoryRatio = 0.0,
            ignoredFileRatio = 0.0,
            unexpectedFiles = 1_000,
        )
        val architecture = project.architecture()
        val fileSystem = project.inMemoryFileSystem()

        val (violations, elapsed) = measureTimedValue { architecture.validate(fileSystem) }
        println("NearbyGuardSpec: ${elapsed.inWholeMilliseconds} ms (limit ${LIMIT.inWholeMilliseconds} ms)")

        elapsed shouldBeLessThan LIMIT
        violations.map { "[${it.label}] ${it.path}" }.sorted() shouldBe project.expectedViolationLabels
        violations.filterNot { it is UnexpectedFile }.shouldBeEmpty()
        // The answer has to be the real one for the time to mean anything: every file gets
        // its three nearest places, and none of them is more than the limit.
        violations.filterIsInstance<UnexpectedFile>().forEach { it.nearby.size shouldBeLessThanOrEqual 3 }
        violations.filterIsInstance<UnexpectedFile>().count { it.nearby.size == 3 } shouldBe 1_000
    }
})
