package me.tbsten.katachi.benchmark

import java.util.concurrent.TimeUnit
import me.tbsten.katachi.benchmark.util.BENCHMARK_SEED
import me.tbsten.katachi.benchmark.util.ProjectOnDisk
import me.tbsten.katachi.benchmark.util.assertNoErrors
import me.tbsten.katachi.benchmark.util.konsistArchitecture
import me.tbsten.katachi.benchmark.util.kontureArchitecture
import me.tbsten.katachi.benchmark.util.ktFileContentArchitecture
import me.tbsten.katachi.check.FileConstraintCheck
import me.tbsten.katachi.check.validate
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.test.synthetic.architecture
import me.tbsten.katachi.test.synthetic.generateProject
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
 * The first check of a fresh JVM, as a user's first `assert()` in a test run sees it: class
 * loading, Konsist's initialisation and the whole check, once, with nothing warmed up.
 *
 * One measured shot per fork and five forks; setting up the project on disk happens before
 * the shot and is not measured. `assert()` itself only runs against the JVM's working
 * directory, so the shot is `validate(fileSystem)` followed by what `assert()` does with the
 * result — fail on any `Error` — against a project with no unknown directories, which passes.
 *
 * - [layoutOnly] is `assert()`: the layout check, no Konsist.
 * - [withKonsist] is `assert(FileConstraintCheck())` with one `konsist { }` per role.
 * - [withKtFileContent] is the same with one `ktFileContent { }` per role instead.
 * - [withKonture] is the same with one `konture { }` per role instead: Konture's
 *   initialisation rather than Konsist's.
 *
 * A quick run (`-Pjmh.quick=true`) runs a warmup shot first, so its numbers are not cold.
 *
 * ## Example 1: run only this benchmark with its own settings
 * ```sh
 * ./gradlew :benchmark:jmh -Pjmh.includes=AssertColdBench
 * ```
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(5)
@Warmup(iterations = 0)
@Measurement(iterations = 1)
open class AssertColdBench {
    @Param("200", "2000")
    @JvmField
    var files: Int = 0

    private lateinit var disk: ProjectOnDisk
    private lateinit var layoutArchitecture: Architecture
    private lateinit var konsistArchitecture: Architecture
    private lateinit var ktFileContentArchitecture: Architecture
    private lateinit var kontureArchitecture: Architecture

    @Setup
    fun setUp() {
        // No unknown directories, so the check passes the way a project's own assert() does.
        val project = generateProject(
            files = files,
            roles = 5,
            modules = 10,
            seed = BENCHMARK_SEED,
            unknownDirectoryRatio = 0.0,
        )
        disk = ProjectOnDisk(project)
        // Building the definitions runs no layout block and reads no file, so it stays out of
        // the shot without warming what the shot measures.
        layoutArchitecture = project.architecture()
        konsistArchitecture = project.konsistArchitecture()
        ktFileContentArchitecture = project.ktFileContentArchitecture()
        kontureArchitecture = project.kontureArchitecture()
    }

    @TearDown
    fun tearDown() {
        disk.delete()
    }

    @Benchmark
    fun layoutOnly() {
        assertNoErrors(layoutArchitecture.validate(disk.fileSystem))
    }

    @Benchmark
    fun withKonsist() {
        assertNoErrors(konsistArchitecture.validate(disk.fileSystem, FileConstraintCheck()))
    }

    @Benchmark
    fun withKtFileContent() {
        assertNoErrors(ktFileContentArchitecture.validate(disk.fileSystem, FileConstraintCheck()))
    }

    @Benchmark
    fun withKonture() {
        assertNoErrors(kontureArchitecture.validate(disk.fileSystem, FileConstraintCheck()))
    }
}
