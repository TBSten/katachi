package me.tbsten.katachi.dsl.internal

import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.FileConstraint
import me.tbsten.katachi.dsl.FileConstraintRange
import me.tbsten.katachi.dsl.KatachiFileConstraintNameException
import me.tbsten.katachi.dsl.LayoutEntry
import me.tbsten.katachi.dsl.LayoutEntryKind
import java.util.Collections

/**
 * One `fileConstraint { }` call, kept as written.
 *
 * What it covers is not decided here: that depends on the block it sits in and on the entries
 * that block turned out to declare, neither of which is known while the DSL is still running.
 * See [FileConstraintSite].
 */
internal class FileConstraintDeclaration(
    val name: String?,
    val declaredAt: DeclarationSite,
    /** How far below its block it reaches. See [FileConstraintSite]. */
    val scope: FileConstraintRange,
    val check: FileConstraint,
) {
    override fun toString(): String = "FileConstraintDeclaration(${name ?: declaredAt})"
}

/**
 * Validates the name, then keeps the declaration.
 *
 * The name is checked here rather than where a report is written, because a definition that
 * cannot be printed is a bad value in the definition — the same class of mistake as an invalid
 * role name, and worth the same immediate refusal.
 */
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

/**
 * The constraints one block of a `layout { }` declared, together with what that block owns.
 *
 * A site is closed when its block finishes, because only then is the subtree it declared
 * complete. What is kept is the nodes themselves rather than paths: a node's path is not known
 * until the tree is flattened, and a wildcard module key produces one site per module with a
 * different resolved path each time.
 */
internal class FileConstraintSite(
    /** The constraints written directly in that block, in the order they were written. */
    val declarations: List<FileConstraintDeclaration>,
    /**
     * The nodes this block owns. The entries of these subtrees are what the constraint covers,
     * minus the ones a `module { }` block injected — see [LayoutNode.synthetic].
     */
    val owned: List<LayoutNode>,
    /**
     * The block's own directory node, or `null` where the block has none: directly under
     * `layout { }`, and inside `":".module { }`, whose directory *is* the project root.
     */
    val anchor: LayoutNode?,
)

/**
 * Which files one constraint covers, as the patterns its own entries declared, and what the
 * `capture("...")` names of those entries take from each file.
 *
 * The rule is [me.tbsten.katachi.check.internal.LayoutIndex.rolesOf]'s, narrowed to one constraint's
 * entries: a file is covered when a file declaration matches it, or when its directory was
 * left open with `anyFile()`. Keeping the two rules in step is what stops a constraint from
 * covering a file the role does not own, or missing one it does.
 */
internal class FileConstraintCoverage(
    /** The entries the block owns. Only the files and the `anyFile()` directories cover anything. */
    covered: List<LayoutEntry>,
) {
    private val entries: List<LayoutEntry> =
        covered.filter { it.kind == LayoutEntryKind.File || it.kind == LayoutEntryKind.AnyFile }
    /** The patterns of the file entries: a file one of them matches is covered. */
    val fileGlobs: List<Glob> = entries.filter { it.kind == LayoutEntryKind.File }.map { it.glob }

    /** The patterns of the `anyFile()` directories: a file directly inside one of them is covered. */
    val anyFileGlobs: List<Glob> = entries.filter { it.kind == LayoutEntryKind.AnyFile }.map { it.glob }

    /**
     * See [me.tbsten.katachi.dsl.FileConstraintSubject.captureNames]. Read-only, because every
     * constraint of the block is handed this same set.
     */
    val captureNames: Set<String> = Collections.unmodifiableSet(
        entries.flatMapTo(LinkedHashSet()) { entry -> entry.captureVariants.flatMap { it.names } },
    )

    fun covers(file: String): Boolean {
        if (fileGlobs.any { it.matches(file) }) return true
        val parent = file.substringBeforeLast('/', missingDelimiterValue = "")
        return parent.isNotEmpty() && anyFileGlobs.any { it.matches(parent) }
    }

    /**
     * See [me.tbsten.katachi.dsl.FileConstraintSubject.captureValuesOf]. Every entry matching
     * [file] has its say, the first to name a value keeping it.
     */
    fun captureValuesOf(file: String): Map<String, String> = merged { it.captureValuesOf(file) }

    /**
     * What [fileName] alone gives the names on the file name level of each file entry, for an
     * example that is only a name.
     */
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
