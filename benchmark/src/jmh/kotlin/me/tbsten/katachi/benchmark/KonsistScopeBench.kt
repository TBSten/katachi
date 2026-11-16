package me.tbsten.katachi.benchmark

import com.lemonappdev.konsist.api.Konsist
import java.util.concurrent.TimeUnit
import me.tbsten.katachi.benchmark.util.FileConstraintFixture
import me.tbsten.katachi.benchmark.util.konsistArchitecture
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
 * Konsist parsing a generated project written to disk, warm: the cost `konsist { }` adds to
 * every run once the JVM and Konsist are initialised.
 *
 * - [parse] is Konsist alone: `scopeFromExternalDirectories` over the project, the call
 *   `:katachi-konsist` makes. Konsist's own regressions show here, and nowhere upstream.
 * - [validateWithKonsist] is the whole check with one `konsist { }` per role, through
 *   `validate(fileSystem, FileConstraintCheck())`: the walk, the parse, and slicing the scope
 *   per constraint.
 *
 * ## Example 1: run only this benchmark, briefly
 * ```sh
 * ./gradlew :benchmark:jmh -Pjmh.includes=KonsistScopeBench -Pjmh.quick=true
 * ```
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(2)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
open class KonsistScopeBench {
    @Param("200", "2000")
    @JvmField
    var files: Int = 0

    private lateinit var fixture: FileConstraintFixture

    @Setup
    fun setUp() {
        fixture = FileConstraintFixture(files) { konsistArchitecture() }
    }

    @TearDown
    fun tearDown() {
        fixture.delete()
    }

    @Benchmark
    fun parse(): Int = Konsist.scopeFromExternalDirectories(listOf(fixture.absolutePath)).files.size

    @Benchmark
    fun validateWithKonsist(): List<Violation> = fixture.validate()
}
