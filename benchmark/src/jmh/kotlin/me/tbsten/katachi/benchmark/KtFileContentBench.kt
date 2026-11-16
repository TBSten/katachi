package me.tbsten.katachi.benchmark

import java.util.concurrent.TimeUnit
import me.tbsten.katachi.benchmark.util.FileConstraintFixture
import me.tbsten.katachi.benchmark.util.ktFileContentArchitecture
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
 * The whole check of a generated project written to disk with one `ktFileContent { }` per role,
 * warm: what the allow list of a file's package, declarations and members adds to every run.
 *
 * Through `validate(fileSystem, FileConstraintCheck())`, like
 * [KonsistScopeBench.validateWithKonsist]: the walk, the parse, and then every line of the block
 * matched against every file the role owns. The block is run once per file, so the cost grows
 * with files x lines. Compare with `KonsistScopeBench.validateWithKonsist` at the same size to
 * see what the matching costs on top of the parse.
 *
 * ## Example 1: run only this benchmark, briefly
 * ```sh
 * ./gradlew :benchmark:jmh -Pjmh.includes=KtFileContentBench -Pjmh.quick=true
 * ```
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(2)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
open class KtFileContentBench {
    @Param("200", "2000")
    @JvmField
    var files: Int = 0

    private lateinit var fixture: FileConstraintFixture

    @Setup
    fun setUp() {
        fixture = FileConstraintFixture(files) { ktFileContentArchitecture() }
    }

    @TearDown
    fun tearDown() {
        fixture.delete()
    }

    @Benchmark
    fun validateWithKtFileContent(): List<Violation> = fixture.validate()
}
