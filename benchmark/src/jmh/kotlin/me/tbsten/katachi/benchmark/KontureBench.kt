package me.tbsten.katachi.benchmark

import java.util.concurrent.TimeUnit
import me.tbsten.katachi.benchmark.util.FileConstraintFixture
import me.tbsten.katachi.benchmark.util.kontureArchitecture
import me.tbsten.katachi.benchmark.util.kontureOnFirstRoleArchitecture
import me.tbsten.katachi.check.Violation
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Fork
import org.openjdk.jmh.annotations.Measurement
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Param
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.TearDown
import org.openjdk.jmh.annotations.Warmup

/**
 * The whole check of a generated project written to disk with `konture { }`, warm: the cost a
 * Konture rule adds to every run once the JVM and Konture are initialised.
 *
 * Through `validate(fileSystem, FileConstraintCheck())`, like
 * [KonsistScopeBench.validateWithKonsist] with the other engine: the walk, Konture's symbol
 * table of every walked `.kt` file, its parse of the covered files, and the rule.
 *
 * - [validateWithKonture] writes one `konture { }` per role, so the rules cover every file.
 * - [validateWithKontureOnOneRole] writes it on the first role alone: the symbol table still
 *   reads every walked `.kt` file, while the parse and the rule take that role's files only.
 *
 * ## Example 1: run only this benchmark, briefly
 * ```sh
 * ./gradlew :benchmark:jmh -Pjmh.includes=KontureBench -Pjmh.quick=true
 * ```
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(2)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
open class KontureBench {
    @Param("200", "2000")
    @JvmField
    var files: Int = 0

    private lateinit var everyRole: FileConstraintFixture
    private lateinit var firstRole: FileConstraintFixture

    @Setup
    fun setUp() {
        everyRole = FileConstraintFixture(files) { kontureArchitecture() }
        firstRole = FileConstraintFixture(files) { kontureOnFirstRoleArchitecture() }
    }

    @TearDown
    fun tearDown() {
        everyRole.delete()
        firstRole.delete()
    }

    @Benchmark
    fun validateWithKonture(): List<Violation> = everyRole.validate()

    @Benchmark
    fun validateWithKontureOnOneRole(): List<Violation> = firstRole.validate()
}
