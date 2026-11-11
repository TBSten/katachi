package me.tbsten.katachi.report.internal

import me.tbsten.katachi.check.AmbiguousLayout
import me.tbsten.katachi.check.CaptureMismatch
import me.tbsten.katachi.check.MissingFile
import me.tbsten.katachi.check.StaleBaselineEntry
import me.tbsten.katachi.check.UncheckedDirectory
import me.tbsten.katachi.check.UncheckedFileConstraint
import me.tbsten.katachi.check.UnexpectedDirectory
import me.tbsten.katachi.check.UnsatisfiedFileConstraint
import me.tbsten.katachi.check.Violation
import me.tbsten.katachi.check.internal.BaselineKey
import me.tbsten.katachi.check.internal.MissingDescription
import me.tbsten.katachi.check.internal.baselineEntryLine
import me.tbsten.katachi.check.internal.reportedDeclaredAt
import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.files.FsPath
import me.tbsten.katachi.internal.fileUri
import me.tbsten.katachi.report.SarifContext

internal const val SRCROOT: String = "%SRCROOT%"

internal class SarifLocation(
    val uri: String,
    val uriBaseId: String?,
    val startLine: Int?,
)

internal class SarifRelatedLocation(
    val location: SarifLocation,
    val message: String,
)

internal class SarifLocations(
    val primary: SarifLocation,
    val related: List<SarifRelatedLocation>,
)

// A prefix the project root does not actually end with is dropped, so a uri and its base never disagree.
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

    val uri: String get() = fileUri(root.value) + "/"
}

internal class SarifLocationResolver(
    private val context: SarifContext,
    private val base: RepositoryBase,
) {
    private val notes = LinkedHashSet<String>()

    val notifications: List<String> get() = notes.toList()

    private val filesByName: Map<String, List<FsPath>> by lazy {
        context.sourceRoots
            .flatMap { root -> context.filesUnder(root) }
            .distinct()
            .groupBy { it.name }
    }

    fun locationsOf(violation: Violation): SarifLocations =
        SarifLocations(primaryOf(violation), relatedOf(violation))

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
            resolvedLocationOf(violation.reportedDeclaredAt)?.let { SarifRelatedLocation(it, "Declared here") },
        )

        is CaptureMismatch -> listOfNotNull(
            resolvedLocationOf(violation.declaredAt)?.let { SarifRelatedLocation(it, "Declared here") },
        )

        is AmbiguousLayout -> violation.claims.drop(1).mapNotNull { claim ->
            resolvedLocationOf(claim.declaredAt)?.let {
                SarifRelatedLocation(it, "Also claimed by ${claim.role.qualifiedName}")
            }
        }

        else -> emptyList()
    }

    private fun resolvedLocationOf(site: DeclarationSite): SarifLocation? =
        (lookup(site) as? SiteLookup.Found)?.let { locationOf(it.file, lineOf(site)) }

    private fun declaredSiteOf(violation: Violation): DeclarationSite? = when (violation) {
        is MissingFile -> violation.declaredAt
        is CaptureMismatch -> violation.declaredAt
        is UnsatisfiedFileConstraint -> violation.reportedDeclaredAt
        is UncheckedFileConstraint -> violation.declaredAt
        is MissingDescription -> violation.declaredAt
        is AmbiguousLayout -> violation.claims.firstOrNull()?.declaredAt
        else -> null
    }

    private fun pathOf(path: String): FsPath {
        val parsed = FsPath.of(path)
        return if (parsed.isAbsolute) parsed else context.projectRoot / path
    }

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

    // Backends outside katachi may give 0 or a negative line; startLine must be at least 1 or the whole upload
    // fails, so treat it as no line.
    private fun lineOf(violation: Violation): Int? =
        (violation as? UnsatisfiedFileConstraint)?.line?.takeIf { it > 0 }

    private sealed interface SiteLookup {
        class Found(val file: FsPath) : SiteLookup

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
