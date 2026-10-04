package me.tbsten.katachi.report.internal

import java.io.File
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.files.FsPath
import me.tbsten.katachi.report.SarifContext

/**
 * The [SarifContext] of one `assert()`: the source roots the property names or, failing that,
 * the conventional ones; the git prefix of [projectRoot]; and where `architecture { }` was
 * written.
 *
 * Only called once the report was asked for. Nothing here -- git included -- runs otherwise.
 */
internal fun sarifContextOf(
    projectRoot: FsPath,
    architecture: Architecture,
    workingDirectory: FsPath,
    environment: SarifEnvironment,
): SarifContext {
    val given = environment.systemProperty(SOURCE_ROOTS_PROPERTY).orEmpty()
        .split(File.pathSeparator)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { FsPath.of(it) }
    val sourceRoots = if (given.isNotEmpty()) given else conventionalSourceRoots(listOf(workingDirectory, projectRoot), environment)
    return SarifContext(
        projectRoot = projectRoot,
        sourceRoots = sourceRoots,
        repositoryPrefix = environment.repositoryPrefix(projectRoot) ?: "",
        architectureDeclaredAt = architecture.declaredAt,
        isFile = environment.isFile,
        readText = environment.readText,
        filesUnder = environment.filesUnder,
    )
}

/**
 * `src/<sourceSet>/kotlin` and `src/<sourceSet>/java` below each of [bases], for every source set
 * directory that exists -- the shape of a Gradle module, which under Gradle is also the working
 * directory of the test. Each base is looked at once however many times it is listed.
 */
internal fun conventionalSourceRoots(bases: List<FsPath>, environment: SarifEnvironment): List<FsPath> =
    bases.distinct().flatMap { base ->
        environment.directoriesUnder(base / "src").flatMap { sourceSet ->
            listOf(sourceSet / "kotlin", sourceSet / "java").filter(environment.isDirectory)
        }
    }.distinct()
