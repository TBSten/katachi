package me.tbsten.katachi.benchmark

import java.io.File
import java.util.concurrent.TimeUnit
import me.tbsten.katachi.check.FileConstraintCheck
import me.tbsten.katachi.check.Violation
import me.tbsten.katachi.check.validate
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.files.KatachiFileSystem
import me.tbsten.katachi.test.synthetic.generateProject
import me.tbsten.katachi.test.synthetic.writeTo
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
 * The whole check of a generated project written to disk with one `konture { }` per role, warm:
 * the cost a Konture rule adds to every run once the JVM and Konture are initialised.
 *
 * Through `validate(fileSystem, FileConstraintCheck())`, like
 * [KonsistScopeBench.validateWithKonsist] with the other engine: the walk, Konture's parse of the
 * files each role owns, and the rule.
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

    private lateinit var directory: File
    private lateinit var architecture: Architecture
    private lateinit var fileSystem: KatachiFileSystem

    @Setup
    fun setUp() {
        val project = generateProject(files = files, roles = 5, modules = 10, seed = BENCHMARK_SEED)
        directory = createProjectDirectory()
        fileSystem = project.writeTo(directory)
        architecture = project.kontureArchitecture()
        requireViolations(architecture.validate(fileSystem, FileConstraintCheck()), project.expectedViolationLabels)
    }

    @TearDown
    fun tearDown() {
        deleteProjectDirectory(directory)
    }

    @Benchmark
    fun validateWithKonture(): List<Violation> = architecture.validate(fileSystem, FileConstraintCheck())
}
