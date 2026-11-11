package me.tbsten.katachi.report.internal

import java.io.File
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.files.FsPath
import me.tbsten.katachi.report.SarifContext

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

internal fun conventionalSourceRoots(bases: List<FsPath>, environment: SarifEnvironment): List<FsPath> =
    bases.distinct().flatMap { base ->
        environment.directoriesUnder(base / "src").flatMap { sourceSet ->
            listOf(sourceSet / "kotlin", sourceSet / "java").filter(environment.isDirectory)
        }
    }.distinct()
