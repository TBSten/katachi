package me.tbsten.katachi.benchmark

import java.util.concurrent.TimeUnit
import me.tbsten.katachi.check.Violation
import me.tbsten.katachi.check.internal.validate
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.files.KatachiFileSystem
import me.tbsten.katachi.test.synthetic.architecture
import me.tbsten.katachi.test.synthetic.generateProject
import me.tbsten.katachi.test.synthetic.inMemoryFileSystem
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
import org.openjdk.jmh.annotations.Warmup

/**
 * The whole layout check of a generated project held in memory: module discovery, layout
 * evaluation, the walk and role matching, with no disk and no Konsist.
 *
 * Measured through `validate(fileSystem)`, the same call `assert()` makes minus the report.
 * The tree is an `IndexedFileSystem`, whose `list` is one map lookup, so the file system does
 * not dominate at 10k files. Scales with files (N) × roles (R) × modules (M): the flattened
 * layout has about R × M entries and every file is matched against them.
 *
 * ## Example 1: run only this benchmark, briefly
 * ```sh
 * ./gradlew :benchmark:jmh -Pjmh.includes=WalkBench -Pjmh.quick=true
 * ```
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(2)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
open class WalkBench {
    @Param("1000", "10000")
    @JvmField
    var files: Int = 0

    @Param("5", "20")
    @JvmField
    var roles: Int = 0

    @Param("10", "100")
    @JvmField
    var modules: Int = 0

    private lateinit var architecture: Architecture
    private lateinit var fileSystem: KatachiFileSystem

    @Setup
    fun setUp() {
        val project = generateProject(files = files, roles = roles, modules = modules, seed = BENCHMARK_SEED)
        architecture = project.architecture()
        fileSystem = project.inMemoryFileSystem()
        requireViolations(architecture.validate(fileSystem), project.expectedViolationLabels)
    }

    @Benchmark
    fun validate(): List<Violation> = architecture.validate(fileSystem)
}
