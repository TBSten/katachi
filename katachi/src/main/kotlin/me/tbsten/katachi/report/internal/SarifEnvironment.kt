package me.tbsten.katachi.report.internal

import me.tbsten.katachi.dsl.files.FsPath

/** The system property naming the file `assert()` writes the SARIF report to. Absent: no report. */
internal const val SARIF_PATH_PROPERTY: String = "katachi.report.sarif"

/**
 * The system property listing the directories a declaration site's file name is looked for in,
 * absolute and joined with `File.pathSeparator`. Absent: `src/<sourceSet>/kotlin` and
 * `src/<sourceSet>/java` of the working directory and of the project root.
 */
internal const val SOURCE_ROOTS_PROPERTY: String = "katachi.report.sourceRoots"

/**
 * Everything the SARIF report reads from outside the definition and the violations: the two
 * system properties, the disk, git, where its warnings go, and how many times this JVM already
 * wrote each file. The same shape as `BaselineEnvironment`, and for the same reason -- a spec
 * hands in fakes for all of it.
 */
internal class SarifEnvironment(
    val systemProperty: (String) -> String? = { System.getProperty(it) },
    // Looked up on each call rather than captured, so a spec swapping `System.err` sees it.
    val standardError: (String) -> Unit = { System.err.println(it) },
    val isFile: (FsPath) -> Boolean = ::realIsFile,
    val isDirectory: (FsPath) -> Boolean = ::realIsDirectory,
    val directoriesUnder: (FsPath) -> List<FsPath> = ::realDirectoriesUnder,
    val filesUnder: (FsPath) -> List<FsPath> = ::realFilesUnder,
    val readText: (FsPath) -> String? = ::realReadText,
    val writeText: (FsPath, String) -> Unit = ::realWriteText,
    val repositoryPrefix: (FsPath) -> String? = ::gitShowPrefix,
    val runs: SarifRuns = SarifRuns.OF_THIS_PROCESS,
)
