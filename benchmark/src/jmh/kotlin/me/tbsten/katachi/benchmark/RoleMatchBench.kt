package me.tbsten.katachi.benchmark

import java.util.concurrent.TimeUnit
import me.tbsten.katachi.check.internal.moduleIndex
import me.tbsten.katachi.dsl.LayoutEntry
import me.tbsten.katachi.dsl.LayoutEntryKind
import me.tbsten.katachi.dsl.Role
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
 * Matching every file of a generated project against the flattened layout: which roles allow
 * each file.
 *
 * The check does this in `LayoutIndex.rolesOf`, which is `internal` to `:katachi` and cannot be
 * called from here. This benchmark runs the same rule on the same inputs with what is public
 * (`@InternalKatachiApi`): each `File` entry's glob against the path, and each `AnyFile` entry's
 * glob against the parent directory, keeping the first entry per role. It follows the cost of
 * the rule — files × entries regex matches — rather than `rolesOf` itself, so an optimisation
 * inside `LayoutIndex` (an index by first segment, say) does not show here; `WalkBench` sees it.
 *
 * ## Example 1: run only this benchmark, briefly
 * ```sh
 * ./gradlew :benchmark:jmh -Pjmh.includes=RoleMatchBench -Pjmh.quick=true
 * ```
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(2)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
open class RoleMatchBench {
    @Param("10000")
    @JvmField
    var files: Int = 0

    @Param("5", "20")
    @JvmField
    var roles: Int = 0

    @Param("10", "100")
    @JvmField
    var modules: Int = 0

    private lateinit var fileEntries: List<LayoutEntry>
    private lateinit var openEntries: List<LayoutEntry>
    private lateinit var paths: List<String>

    @Setup
    fun setUp() {
        val project = generateProject(files = files, roles = roles, modules = modules, seed = BENCHMARK_SEED)
        val architecture = project.architecture()
        val fileSystem = project.inMemoryFileSystem()
        val entries = architecture.flattenLayout(moduleIndex(fileSystem, project.root, architecture.moduleResolver))
        fileEntries = entries.filter { it.kind == LayoutEntryKind.File }
        openEntries = entries.filter { it.kind == LayoutEntryKind.AnyFile }
        paths = project.roles.flatMap { it.files }

        // Every role file is allowed by its own role and by nothing else.
        for (role in project.roles) {
            for (path in role.files) {
                val matched = rolesOf(path).map { it.name }
                check(matched == listOf(role.name)) { "$path matched $matched, expected [${role.name}]" }
            }
        }
    }

    @Benchmark
    fun matchEveryFile(): Int = paths.sumOf { rolesOf(it).size }

    private fun rolesOf(file: String): Collection<Role> {
        val matched = LinkedHashMap<Role, LayoutEntry>()
        for (entry in fileEntries) {
            if (entry.glob.matches(file)) matched.putIfAbsent(entry.role, entry)
        }
        val directory = file.substringBeforeLast('/', missingDelimiterValue = "")
        if (directory.isNotEmpty()) {
            for (entry in openEntries) {
                if (entry.glob.matches(directory)) matched.putIfAbsent(entry.role, entry)
            }
        }
        return matched.keys
    }
}
