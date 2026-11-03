package me.tbsten.katachi.dsl.internal

import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.FileConstraint
import me.tbsten.katachi.dsl.FileConstraintRange
import me.tbsten.katachi.dsl.KatachiFileConstraintNameException
import me.tbsten.katachi.dsl.LayoutEntry
import me.tbsten.katachi.dsl.LayoutEntryKind
import java.util.Collections

internal class FileConstraintDeclaration(
    val name: String?,
    val declaredAt: DeclarationSite,
    val scope: FileConstraintRange,
    val check: FileConstraint,
) {
    override fun toString(): String = "FileConstraintDeclaration(${name ?: declaredAt})"
}

// Validated here, not at report time: a definition that cannot be printed is a bad value in the definition.
internal fun fileConstraintDeclarationOf(
    name: String?,
    declaredAt: DeclarationSite,
    scope: FileConstraintRange,
    check: FileConstraint,
): FileConstraintDeclaration {
    if (name != null && (name.isBlank() || name.any { it == '\n' || it == '\r' })) {
        throw KatachiFileConstraintNameException(name = name, declaredAt = declaredAt)
    }
    return FileConstraintDeclaration(name = name, declaredAt = declaredAt, scope = scope, check = check)
}

// Holds nodes, not paths: paths exist only after flattening, and a wildcard module key makes one site per module.
internal class FileConstraintSite(
    val declarations: List<FileConstraintDeclaration>,
    // Excludes entries a `module { }` block injected; see LayoutNode.synthetic.
    val owned: List<LayoutNode>,
    // null directly under `layout { }` and inside `":".module { }`, whose directory is the project root.
    val anchor: LayoutNode?,
)

// Same rule as LayoutIndex.rolesOf, narrowed to one constraint's entries.
internal class FileConstraintCoverage(
    covered: List<LayoutEntry>,
) {
    private val entries: List<LayoutEntry> =
        covered.filter { it.kind == LayoutEntryKind.File || it.kind == LayoutEntryKind.AnyFile }
    val fileGlobs: List<Glob> = entries.filter { it.kind == LayoutEntryKind.File }.map { it.glob }

    val anyFileGlobs: List<Glob> = entries.filter { it.kind == LayoutEntryKind.AnyFile }.map { it.glob }

    // Read-only: every constraint of the block is handed this same set.
    val captureNames: Set<String> = Collections.unmodifiableSet(
        entries.flatMapTo(LinkedHashSet()) { entry -> entry.captureVariants.flatMap { it.names } },
    )

    fun covers(file: String): Boolean = entries.any { it.allows(file) }

    // Every matching entry has its say; the first to name a value keeps it.
    fun captureValuesOf(file: String): Map<String, String> = merged { it.captureValuesOf(file) }

    fun captureValuesOfFileName(fileName: String): Map<String, String> =
        merged { it.captureValuesOfFileName(fileName) }

    private inline fun merged(valuesOf: (LayoutEntry) -> Map<String, String>): Map<String, String> {
        val values = LinkedHashMap<String, String>()
        for (entry in entries) {
            for ((name, value) in valuesOf(entry)) values.putIfAbsent(name, value)
        }
        return values
    }

    override fun toString(): String =
        "FileConstraintCoverage(files=${fileGlobs.size}, anyFile=${anyFileGlobs.size})"
}
