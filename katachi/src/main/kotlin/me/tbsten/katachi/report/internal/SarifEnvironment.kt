package me.tbsten.katachi.report.internal

import me.tbsten.katachi.dsl.files.FsPath

internal const val SARIF_PATH_PROPERTY: String = "katachi.report.sarif"

internal const val SOURCE_ROOTS_PROPERTY: String = "katachi.report.sourceRoots"

internal class SarifEnvironment(
    val systemProperty: (String) -> String? = { System.getProperty(it) },
    // Looked up on each call rather than captured, so a spec swapping `System.err` sees it.
    val standardError: (String) -> Unit = { System.err.println(it) },
    val isFile: (FsPath) -> Boolean = ::realIsFile,
    val isDirectory: (FsPath) -> Boolean = ::realIsDirectory,
    val directoriesUnder: (FsPath) -> List<FsPath> = ::realDirectoriesUnder,
    val filesUnder: (FsPath) -> List<FsPath> = ::realFilesUnder,
    val readText: (FsPath) -> String? = ::realReadText,
    // Writes a file that is not there yet and answers true, or answers false and leaves an existing one alone.
    val writeNewText: (FsPath, String) -> Boolean = ::realWriteNewText,
    val repositoryPrefix: (FsPath) -> String? = ::gitShowPrefix,
)
