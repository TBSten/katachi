package me.tbsten.katachi.benchmark

import java.util.concurrent.TimeUnit
import me.tbsten.katachi.check.internal.moduleIndex
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.LayoutEntry
import me.tbsten.katachi.dsl.files.FsPath
import me.tbsten.katachi.dsl.files.KatachiFileSystem
import me.tbsten.katachi.dsl.internal.ModuleIndex
import me.tbsten.katachi.dsl.internal.flattenLayout
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
 * Evaluating every `layout { }` block of a generated definition and flattening it, with
 * `":modules:*".module { }` expanded against the project's modules.
 *
 * `flattenLayout` is the `@InternalKatachiApi` face of the evaluation the check runs first
 * (`evaluateLayout`, which also keeps the file constraints). Every DSL call records its
 * declaration site and every entry compiles a glob, so the cost grows with roles × modules.
 * [discoverAndFlatten] adds the module discovery the check runs before it.
 *
 * ## Example 1: run only this benchmark, briefly
 * ```sh
 * ./gradlew :benchmark:jmh -Pjmh.includes=LayoutEvalBench -Pjmh.quick=true
 * ```
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Fork(2)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
open class LayoutEvalBench {
    @Param("5", "20")
    @JvmField
    var roles: Int = 0

    @Param("10", "100")
    @JvmField
    var modules: Int = 0

    private lateinit var architecture: Architecture
    private lateinit var fileSystem: KatachiFileSystem
    private lateinit var root: FsPath
    private lateinit var index: ModuleIndex

    @Setup
    fun setUp() {
        // Enough files for every module to hold some, and no more: the file count only matters
        // to discovery, which walks every directory outside `src` and `build`.
        val project = generateProject(files = 2_000, roles = roles, modules = modules, seed = BENCHMARK_SEED)
        architecture = project.architecture()
        fileSystem = project.inMemoryFileSystem()
        root = project.root
        index = moduleIndex(fileSystem, root, architecture.moduleResolver)
        check(architecture.flattenLayout(index).isNotEmpty()) { "The generated layout declares nothing." }
    }

    @Benchmark
    fun flatten(): List<LayoutEntry> = architecture.flattenLayout(index)

    @Benchmark
    fun discoverAndFlatten(): List<LayoutEntry> =
        architecture.flattenLayout(moduleIndex(fileSystem, root, architecture.moduleResolver))
}
