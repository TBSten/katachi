package me.tbsten.katachi.report

import java.io.File
import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.check.Violation
import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.files.FsPath
import me.tbsten.katachi.internal.KatachiVersion
import me.tbsten.katachi.report.internal.SarifRuns
import me.tbsten.katachi.report.internal.realFilesUnder
import me.tbsten.katachi.report.internal.realIsFile
import me.tbsten.katachi.report.internal.realReadText
import me.tbsten.katachi.report.internal.realWriteText
import me.tbsten.katachi.report.internal.render
import me.tbsten.katachi.report.internal.sarifDocumentOf
import me.tbsten.katachi.report.internal.writeSarifText

/**
 * What a SARIF document needs beyond the violations themselves: where the project is, where to
 * look for the files that declared things, and how to reach the disk.
 *
 * `assert()` builds one of these by itself from `-Dkatachi.report.sarif` and
 * `-Dkatachi.report.sourceRoots`. Build one by hand to turn violations you already hold into
 * SARIF -- a processor of your own, say -- or, in a test, to keep the disk out of it: the three
 * functions at the end are the only way the conversion touches a file.
 *
 * ## Example 1: write the violations of a run as SARIF from a processor of your own
 * ```kt
 * val context = SarifContext(
 *     projectRoot = FsPath.of("/w/repo/android"),
 *     sourceRoots = listOf(FsPath.of("/w/repo/android/architecture-test/src/test/kotlin")),
 *     repositoryPrefix = "android/",
 *     architectureDeclaredAt = projectArchitecture.declaredAt,
 * )
 * violations.writeSarif(FsPath.of("/w/repo/android/build/reports/katachi/katachi.sarif"), context)
 * ```
 *
 * @constructor Takes the project root, and optionally where to look for the files that declared
 *   things, the repository prefix, where the architecture was declared, the version to report,
 *   and how to reach the disk.
 * @see toSarif
 * @see writeSarif
 */
@ExperimentalKatachiApi
public class SarifContext(
    /** The project root, absolute. Every [Violation.path] is relative to it. */
    public val projectRoot: FsPath,
    /**
     * The directories a [DeclarationSite.fileName] is looked for in, recursively. Empty means no
     * declaration site can be placed, and every violation without a file of its own falls back
     * to the file name alone.
     */
    public val sourceRoots: List<FsPath> = emptyList(),
    /**
     * Where [projectRoot] sits inside its repository, as `git rev-parse --show-prefix` prints it:
     * `"android/"` with the trailing `/`, or `""` when the project root is the repository root.
     * Every `uri` is written below the repository root so that GitHub can match it to a file
     * of the checkout.
     */
    public val repositoryPrefix: String = "",
    /**
     * Where `architecture { }` was called -- the last place a violation can point at when it has
     * neither a file nor a declaration site. `null` leaves only the file name as the last resort.
     */
    public val architectureDeclaredAt: DeclarationSite? = null,
    /** What `tool.driver.semanticVersion` says. The running katachi's own version by default. */
    public val toolVersion: String = KatachiVersion.current(),
    /** Whether a regular file sits at the path. Swapped for a fake in a test. */
    public val isFile: (FsPath) -> Boolean = ::realIsFile,
    /** The text of the file at the path, or `null` when there is none. Reads the baseline file. */
    public val readText: (FsPath) -> String? = ::realReadText,
    /** Every regular file at or below the directory, sorted; empty when it is not a directory. */
    public val filesUnder: (FsPath) -> List<FsPath> = ::realFilesUnder,
)

/**
 * These violations as a SARIF 2.1.0 document, the text of a `.sarif` file.
 *
 * A pure function of its inputs: no time stamp, no fingerprint, no machine-specific path inside
 * a message, so two runs over the same violations give the same text and a golden file can hold
 * it. An empty list gives a document with an empty `results`, which tells GitHub to close the
 * alerts an earlier upload opened.
 *
 * One rule per violation label (`katachi.UnexpectedFile`); the role, the constraint and the
 * declaration site ride in each result's `properties` and in its message, which is the report
 * block without its first line.
 *
 * ## Example 1: convert the violations a check found
 * ```kt
 * val sarif: String = projectArchitecture.validate().toSarif(SarifContext(projectRoot = FsPath.of("/w/repo")))
 * ```
 *
 * @see SarifContext
 * @see writeSarif
 */
@ExperimentalKatachiApi
public fun List<Violation>.toSarif(context: SarifContext): String = sarifDocumentOf(this, context).render()

/**
 * [toSarif], written to [file] as UTF-8 with the directories above it created as needed.
 *
 * One file holds one run. From the second call in the same JVM that names the same [file] on,
 * the document goes to a numbered sibling instead -- `katachi-2.sarif`, `katachi-3.sarif` -- so
 * that two architecture tests of one module do not overwrite each other, and the path actually
 * written is what comes back. A relative [file] is resolved against the JVM's working directory.
 *
 * ## Example 1: write the SARIF file a CI step uploads
 * ```kt
 * val written: FsPath = violations.writeSarif(
 *     FsPath.of("build/reports/katachi/katachi.sarif"),
 *     SarifContext(projectRoot = FsPath.of("/w/repo")),
 * )
 * ```
 *
 * @return the file the document was written to: [file], or its numbered sibling.
 * @throws java.io.IOException when the file cannot be written. `assert()` catches this itself
 *   and says so on standard error rather than failing the check over it.
 * @see toSarif
 */
@ExperimentalKatachiApi
public fun List<Violation>.writeSarif(file: FsPath, context: SarifContext): FsPath {
    val absolute = if (file.isAbsolute) file else FsPath.of(File(file.value).absolutePath)
    return writeSarifText(absolute, toSarif(context), SarifRuns.OF_THIS_PROCESS, ::realWriteText)
}
