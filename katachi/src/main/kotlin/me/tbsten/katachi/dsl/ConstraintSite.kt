package me.tbsten.katachi.dsl

/**
 * One `constraint { }` call, kept as written.
 *
 * What it covers is not decided here: that depends on the block it sits in and on the entries
 * that block turned out to declare, neither of which is known while the DSL is still running.
 * See [ConstraintSite].
 */
internal class ConstraintDeclaration(
    val name: String?,
    val declaredAt: DeclarationSite,
    val check: FileSetConstraint,
) {
    override fun toString(): String = "ConstraintDeclaration(${name ?: declaredAt})"
}

/**
 * Validates the name, then keeps the declaration.
 *
 * The name is checked here rather than where a report is written, because a definition that
 * cannot be printed is a bad value in the definition — the same class of mistake as an invalid
 * role name, and worth the same immediate refusal.
 */
internal fun constraintDeclarationOf(
    name: String?,
    declaredAt: DeclarationSite,
    check: FileSetConstraint,
): ConstraintDeclaration {
    if (name != null && (name.isBlank() || name.any { it == '\n' || it == '\r' })) {
        throw KatachiConstraintNameException(name = name, declaredAt = declaredAt)
    }
    return ConstraintDeclaration(name = name, declaredAt = declaredAt, check = check)
}

/**
 * The constraints one block of a `layout { }` declared, together with what that block owns.
 *
 * A site is closed when its block finishes, because only then is the subtree it declared
 * complete. What is kept is the nodes themselves rather than paths: a node's path is not known
 * until the tree is flattened, and a wildcard module key produces one site per module with a
 * different resolved path each time.
 */
internal class ConstraintSite(
    /** The constraints written directly in that block, in the order they were written. */
    val declarations: List<ConstraintDeclaration>,
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
 * Which files one constraint covers, as the patterns its own entries declared.
 *
 * The rule is [me.tbsten.katachi.scan.LayoutIndex.rolesOf]'s, narrowed to one constraint's
 * entries: a file is covered when a file declaration matches it, or when its directory was
 * left open with `anyFile()`. Keeping the two rules in step is what stops a constraint from
 * covering a file the role does not own, or missing one it does.
 */
internal class ConstraintCoverage(
    private val fileGlobs: List<Glob>,
    private val anyFileGlobs: List<Glob>,
) {
    fun covers(file: String): Boolean {
        if (fileGlobs.any { it.matches(file) }) return true
        val parent = file.substringBeforeLast('/', missingDelimiterValue = "")
        return parent.isNotEmpty() && anyFileGlobs.any { it.matches(parent) }
    }

    /** Whether this constraint could not cover any file at all, whatever the project holds. */
    val isEmpty: Boolean get() = fileGlobs.isEmpty() && anyFileGlobs.isEmpty()

    override fun toString(): String =
        "ConstraintCoverage(files=${fileGlobs.size}, anyFile=${anyFileGlobs.size})"
}
