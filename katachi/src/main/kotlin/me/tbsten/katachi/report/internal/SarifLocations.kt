package me.tbsten.katachi.report.internal

import me.tbsten.katachi.check.AmbiguousLayout
import me.tbsten.katachi.check.MissingDescription
import me.tbsten.katachi.check.MissingFile
import me.tbsten.katachi.check.StaleBaselineEntry
import me.tbsten.katachi.check.UncheckedDirectory
import me.tbsten.katachi.check.UncheckedFileConstraint
import me.tbsten.katachi.check.UnexpectedDirectory
import me.tbsten.katachi.check.UnsatisfiedFileConstraint
import me.tbsten.katachi.check.Violation
import me.tbsten.katachi.check.internal.BaselineKey
import me.tbsten.katachi.check.internal.baselineEntryLine
import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.files.FsPath
import me.tbsten.katachi.internal.fileUri
import me.tbsten.katachi.report.SarifContext

/** The `uriBaseId` every path below the repository root is written against. */
internal const val SRCROOT: String = "%SRCROOT%"

/** One `physicalLocation`: where, and -- when known -- which line. */
internal class SarifLocation(
    val uri: String,
    val uriBaseId: String?,
    val startLine: Int?,
)

/** One `relatedLocations[]` entry: a location and what it is to the violation. */
internal class SarifRelatedLocation(
    val location: SarifLocation,
    val message: String,
)

/** Where one result points: its `locations[0]` and its `relatedLocations[]`. */
internal class SarifLocations(
    val primary: SarifLocation,
    val related: List<SarifRelatedLocation>,
)

/**
 * The repository root and the prefix from it down to the project root, checked against each
 * other: a prefix the project root does not actually end with is dropped, so that a `uri` and
 * the base it is relative to can never disagree.
 */
internal class RepositoryBase(projectRoot: FsPath, requestedPrefix: String) {
    val prefix: String
    val root: FsPath

    init {
        val segments = requestedPrefix.split('/').filter { it.isNotEmpty() }
        val matches = segments.isNotEmpty() &&
            projectRoot.segments.size >= segments.size &&
            projectRoot.segments.takeLast(segments.size) == segments
        if (matches) {
            var up: FsPath = projectRoot
            repeat(segments.size) { up = up.parent ?: projectRoot }
            prefix = segments.joinToString("/") + "/"
            root = up
        } else {
            prefix = ""
            root = projectRoot
        }
    }

    /** The `originalUriBaseIds` entry: the repository root as a `file:///.../` URI. */
    val uri: String get() = fileUri(root.value) + "/"
}

/**
 * Decides where each violation is placed, and remembers why any of them could not be placed at
 * the line that declared it. Built once per document: the source roots are walked once and the
 * index of file names is kept.
 */
internal class SarifLocationResolver(
    private val context: SarifContext,
    private val base: RepositoryBase,
) {
    private val notes = LinkedHashSet<String>()

    /** The reasons a declaration site stayed unresolved, one sentence each, first seen first. */
    val notifications: List<String> get() = notes.toList()

    private val filesByName: Map<String, List<FsPath>> by lazy {
        context.sourceRoots
            .flatMap { root -> context.filesUnder(root) }
            .distinct()
            .groupBy { it.name }
    }

    fun locationsOf(violation: Violation): SarifLocations =
        SarifLocations(primaryOf(violation), relatedOf(violation))

    /**
     * Where the result points, in the order the design fixes: the file itself when it exists,
     * the line that declared it, the `architecture { }` call, and as a last resort a bare name.
     */
    private fun primaryOf(violation: Violation): SarifLocation {
        if (violation is StaleBaselineEntry) {
            val baselineFile = context.projectRoot / violation.baselinePath
            if (context.isFile(baselineFile)) return locationOf(baselineFile, baselineEntryLineOf(violation, baselineFile))
            return fallbackOf(violation, ownSite = null, ownLookup = null)
        }
        val file = pathOf(violation.path)
        if (context.isFile(file)) return locationOf(file, lineOf(violation))
        val ownSite = declaredSiteOf(violation)
        val ownLookup = ownSite?.let { lookup(it) }
        if (ownLookup is SiteLookup.Found) return locationOf(ownLookup.file, lineOf(ownSite))
        return fallbackOf(violation, ownSite, ownLookup)
    }

    private fun fallbackOf(violation: Violation, ownSite: DeclarationSite?, ownLookup: SiteLookup?): SarifLocation {
        val architectureSite = context.architectureDeclaredAt
        val architectureLookup = architectureSite?.let { lookup(it) }
        if (architectureLookup is SiteLookup.Found) {
            note(ownLookup, AT_DEFINITION)
            return locationOf(architectureLookup.file, lineOf(architectureSite))
        }
        note(ownLookup ?: architectureLookup, AT_FILE_NAME)
        val name = ownSite?.fileName ?: architectureSite?.fileName ?: violation.path
        return SarifLocation(uri = sarifUriOf(name), uriBaseId = SRCROOT, startLine = null)
    }

    private fun relatedOf(violation: Violation): List<SarifRelatedLocation> = when (violation) {
        is MissingFile -> listOf(
            SarifRelatedLocation(locationOf(pathOf(violation.path), null), "The declared file, which does not exist"),
        )

        is UnexpectedDirectory -> listOf(
            SarifRelatedLocation(locationOf(pathOf(violation.path), null), "The directory nothing declared"),
        )

        is UncheckedDirectory -> listOf(
            SarifRelatedLocation(locationOf(pathOf(violation.path), null), "The directory the check failed at"),
        )

        is UnsatisfiedFileConstraint -> listOfNotNull(
            resolvedLocationOf(violation.declaredAt)?.let { SarifRelatedLocation(it, "Declared here") },
        )

        is AmbiguousLayout -> violation.claims.drop(1).mapNotNull { claim ->
            resolvedLocationOf(claim.declaredAt)?.let {
                SarifRelatedLocation(it, "Also claimed by ${claim.role.qualifiedName}")
            }
        }

        else -> emptyList()
    }

    /** [site] as a location when it resolves to one file, and nothing said about it otherwise. */
    private fun resolvedLocationOf(site: DeclarationSite): SarifLocation? =
        (lookup(site) as? SiteLookup.Found)?.let { locationOf(it.file, lineOf(site)) }

    /** The declaration site a violation of this type carries, or `null` when it has none. */
    private fun declaredSiteOf(violation: Violation): DeclarationSite? = when (violation) {
        is MissingFile -> violation.declaredAt
        is UnsatisfiedFileConstraint -> violation.declaredAt
        is UncheckedFileConstraint -> violation.declaredAt
        is MissingDescription -> violation.declaredAt
        is AmbiguousLayout -> violation.claims.firstOrNull()?.declaredAt
        else -> null
    }

    /** A violation's path as an absolute path: below the project root unless it already is one. */
    private fun pathOf(path: String): FsPath {
        val parsed = FsPath.of(path)
        return if (parsed.isAbsolute) parsed else context.projectRoot / path
    }

    /**
     * [file] as a location: relative to the repository root when it sits below the project
     * root, an absolute `file:///` URI with no base when it does not (source roots can point
     * anywhere). Either way the path is percent-encoded into a URI.
     */
    private fun locationOf(file: FsPath, line: Int?): SarifLocation {
        val relative = file.relativeTo(context.projectRoot)
            ?: return SarifLocation(fileUri(file.value), uriBaseId = null, startLine = line)
        val uri = if (relative.isEmpty()) base.prefix.ifEmpty { "." } else base.prefix + relative
        return SarifLocation(sarifUriOf(uri), SRCROOT, line)
    }

    private fun lookup(site: DeclarationSite): SiteLookup {
        if (site == DeclarationSite.Unknown || site.fileName == DeclarationSite.Unknown.fileName) return SiteLookup.Missing(null)
        if (context.sourceRoots.isEmpty()) return SiteLookup.Missing(NO_SOURCE_ROOTS)
        val matches = filesByName[site.fileName].orEmpty()
        return when (matches.size) {
            1 -> SiteLookup.Found(matches.single())
            0 -> SiteLookup.Missing(
                "Declaration site $site was not found under any source root (${context.sourceRoots.joinToString(", ") { display(it) }}).",
            )

            else -> SiteLookup.Missing(
                "Declaration site $site matches ${matches.size} files (${matches.joinToString(", ") { display(it) }}), so it was not resolved.",
            )
        }
    }

    private fun note(lookup: SiteLookup?, landing: String) {
        val reason = (lookup as? SiteLookup.Missing)?.reason ?: return
        val subject = if (reason == NO_SOURCE_ROOTS) "Violations declared in the DSL were" else "The violation was"
        notes += "$reason $subject placed at $landing."
    }

    /** A path for a notification: relative to the project root when it can be, deterministic either way. */
    private fun display(path: FsPath): String {
        val relative = path.relativeTo(context.projectRoot) ?: return path.value
        return relative.ifEmpty { "." }
    }

    private fun baselineEntryLineOf(violation: StaleBaselineEntry, file: FsPath): Int? {
        val text = context.readText(file) ?: return null
        val key = BaselineKey(
            check = violation.check,
            rule = violation.rule,
            path = violation.path,
            role = violation.role,
            constraint = violation.constraint,
            declaration = violation.declaration,
        )
        val entry = baselineEntryLine(key, violation.allowed)
        val index = text.lineSequence().indexOfFirst { entry in it }
        return if (index < 0) null else index + 1
    }

    private fun lineOf(site: DeclarationSite): Int? = site.lineNumber.takeIf { it > 0 }

    /**
     * The line a violation carries for the file itself, when it is one SARIF accepts.
     * `UnsatisfiedFileConstraint.line` is filled in by backends outside katachi, and a `0` or a
     * negative number from one of them would fail the schema (`startLine` is at least 1) and
     * with it the whole upload, so it is treated as "no line".
     */
    private fun lineOf(violation: Violation): Int? =
        (violation as? UnsatisfiedFileConstraint)?.line?.takeIf { it > 0 }

    private sealed interface SiteLookup {
        class Found(val file: FsPath) : SiteLookup

        /** Not resolved, with the sentence saying why -- `null` for a site that was never captured. */
        class Missing(val reason: String?) : SiteLookup
    }

    private companion object {
        const val AT_DEFINITION: String = "the architecture definition instead"
        const val AT_FILE_NAME: String = "the file name alone, which GitHub cannot show"
        const val NO_SOURCE_ROOTS: String =
            "No source root was given with -Dkatachi.report.sourceRoots and none was found under src/*/kotlin " +
                "or src/*/java of the project root, so declaration sites (FileName.kt:NN) could not be resolved to files."
    }
}
