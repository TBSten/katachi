package me.tbsten.katachi.benchmark.util

import java.io.File
import java.nio.file.Files
import me.tbsten.katachi.check.FileConstraintCheck
import me.tbsten.katachi.check.Violation
import me.tbsten.katachi.check.validate
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.files.KatachiFileSystem
import me.tbsten.katachi.test.synthetic.SyntheticProject
import me.tbsten.katachi.test.synthetic.generateProject
import me.tbsten.katachi.test.synthetic.writeTo

/** The seed every benchmark generates its project from, so two runs measure the same tree. */
internal const val BENCHMARK_SEED: Long = 42L

/** [project] written to a temporary directory. Call [delete] from the benchmark's `@TearDown`. */
internal class ProjectOnDisk(val project: SyntheticProject) {
    private val directory: File = Files.createTempDirectory("katachi-benchmark-").toFile()

    val fileSystem: KatachiFileSystem = project.writeTo(directory)

    /** For a tool that takes a path instead of a [KatachiFileSystem]. */
    val absolutePath: String get() = directory.absolutePath

    fun delete() {
        directory.deleteRecursively()
    }
}

/**
 * What a benchmark of one file constraint sets up: a generated project on disk, and the
 * definition [architectureOf] builds for it.
 *
 * Creating it checks the project once, so a definition that stopped matching the generated
 * tree, or a constraint the generated classes no longer satisfy, fails the setup instead of
 * being timed.
 */
internal class FileConstraintFixture(files: Int, architectureOf: SyntheticProject.() -> Architecture) {
    private val disk = ProjectOnDisk(generateProject(files = files, roles = 5, modules = 10, seed = BENCHMARK_SEED))
    private val architecture: Architecture = disk.project.architectureOf()

    val absolutePath: String get() = disk.absolutePath

    init {
        requireViolations(validate(), disk.project.expectedViolationLabels)
    }

    fun validate(): List<Violation> = architecture.validate(disk.fileSystem, FileConstraintCheck())

    fun delete() {
        disk.delete()
    }
}
