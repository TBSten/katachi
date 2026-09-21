package me.tbsten.katachi.dsl


/**
 * One node of the tree a `layout { }` block builds while it is evaluated.
 *
 * The tree is short-lived: it exists between "run the deferred block" and "flatten it into
 * [LayoutEntry] values", and nothing outside the DSL ever sees it.
 */
internal class LayoutNode(
    /** One path segment, possibly holding `*` or `**`. Empty for the synthetic root. */
    val segment: String,
    /** Where the declaration that created this node was written. */
    val declaredAt: DeclarationSite,
    /** Whether this node is a file. A file never has children. */
    val isFile: Boolean,
) {
    var parent: LayoutNode? = null
    val children: MutableList<LayoutNode> = mutableListOf()

    var description: String? = null

    /** Set by `optional()`. Only read for a file. */
    var optional: Boolean = false

    /** Set by `ignore()`, in either of its two spellings. */
    var ignored: Boolean = false

    /** Set by `anyFile()`. */
    var anyFile: Boolean = false

    fun add(child: LayoutNode) {
        child.parent?.children?.remove(child)
        child.parent = this
        children += child
    }

    /** Marks every file at or below this node optional. See [LayoutModule.optional]. */
    fun markFilesOptional() {
        if (isFile) optional = true
        children.forEach { it.markFilesOptional() }
    }

    /** Path from the root of the block being evaluated, for diagnostics. */
    fun pathFromDeclaration(): String {
        val segments = generateSequence(this) { it.parent }
            .map { it.segment }
            .filter { it.isNotEmpty() }
            .toList()
            .asReversed()
        return segments.joinToString("/")
    }

    override fun toString(): String = "LayoutNode(${pathFromDeclaration()})"
}

/** The outermost and innermost node a single key created. See [LayoutDirectory]. */
internal class Chain(val top: LayoutNode, val leaf: LayoutNode)

/**
 * Splits a key such as `"app/ios"` into its levels.
 *
 * An empty level is rejected here rather than left to the glob compiler, because the key as
 * the user wrote it is still at hand and makes a far better message than the joined path.
 */
private fun splitKey(key: String): List<String> {
    val segments = key.split('/')
    if (segments.any { it.isEmpty() }) {
        throw KatachiGlobSyntaxException(key, GlobProblem.EmptyLayoutKeyLevel)
    }
    return segments
}

/** Declares [key], level by level, below [parent]. */
internal fun chainUnder(
    parent: LayoutNode,
    key: String,
    isFile: Boolean,
    declaredAt: DeclarationSite,
): Chain {
    val segments = splitKey(key)
    var current = parent
    var top: LayoutNode? = null
    segments.forEachIndexed { index, segment ->
        // Only the last level of a file key is the file itself; the levels above it are the
        // directories that hold it.
        val node = LayoutNode(
            segment = segment,
            declaredAt = declaredAt,
            isFile = isFile && index == segments.lastIndex,
        )
        current.add(node)
        if (top == null) top = node
        current = node
    }
    return Chain(top = top ?: throw KatachiEmptyLayoutChainException(key), leaf = current)
}
