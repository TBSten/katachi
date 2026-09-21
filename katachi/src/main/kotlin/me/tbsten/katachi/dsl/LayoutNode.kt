package me.tbsten.katachi.dsl

import me.tbsten.katachi.check.GlobSyntaxException

/**
 * One node of the tree a `layout { }` block builds while it is evaluated.
 *
 * The tree is short-lived: it exists between "run the deferred block" and "flatten it into
 * [LayoutEntry] values", and nothing outside this file ever sees it.
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
private class Chain(val top: LayoutNode, val leaf: LayoutNode)

/**
 * Splits a key such as `"app/ios"` into its levels.
 *
 * An empty level is rejected here rather than left to the glob compiler, because the key as
 * the user wrote it is still at hand and makes a far better message than the joined path.
 */
private fun splitKey(key: String): List<String> {
    val segments = key.split('/')
    if (segments.any { it.isEmpty() }) {
        throw GlobSyntaxException(
            "The layout key `$key` has an empty level. Two `/` in a row, a leading `/`, or a " +
                "trailing `/` matches nothing; paths in `layout { }` are always relative to the " +
                "project root.",
        )
    }
    return segments
}

private fun chainUnder(
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
    return Chain(top = checkNotNull(top), leaf = current)
}

/**
 * Implements both receivers of the layout DSL. The root of a `layout { }` block is handed
 * out as [LayoutScope], which hides `description`, `anyFile()` and `ignore()`; a directory
 * block is handed out as [LayoutDirectoryScope], which shows them.
 *
 * `/` works by re-parenting. `"gradle" / "libs.versions.toml".file()` evaluates the right
 * side first, so the file is declared in this scope and then moved under the directory the
 * left side created. Moving the *outermost* node of the right side is what makes a
 * multi-level key such as `"x" / "app/ios" { }` land as `x/app/ios`.
 */
internal class LayoutScopeImpl(private val container: LayoutNode) : LayoutDirectoryScope {
    override var description: String?
        get() = container.description
        set(value) {
            container.description = value
        }

    override fun anyFile() {
        container.anyFile = true
    }

    override fun ignore() {
        container.ignored = true
    }

    override operator fun String.invoke(block: LayoutDirectoryScope.() -> Unit): LayoutDirectory {
        val chain = chainUnder(container, this, isFile = false, declaredAt = captureDeclarationSite())
        LayoutScopeImpl(chain.leaf).block()
        return LayoutDirectory(top = chain.top, leaf = chain.leaf)
    }

    override fun String.file(): LayoutFile = declareFile(this)

    override fun String.ktFile(): LayoutFile = declareFile("$this.kt")

    override fun String.ktsFile(): LayoutFile = declareFile("$this.kts")

    override fun String.ignore(): LayoutDirectory {
        val chain = chainUnder(container, this, isFile = false, declaredAt = captureDeclarationSite())
        chain.leaf.ignored = true
        return LayoutDirectory(top = chain.top, leaf = chain.leaf)
    }

    override operator fun String.div(child: String): LayoutDirectory {
        val declaredAt = captureDeclarationSite()
        val left = chainUnder(container, this, isFile = false, declaredAt = declaredAt)
        val right = chainUnder(left.leaf, child, isFile = false, declaredAt = declaredAt)
        return LayoutDirectory(top = left.top, leaf = right.leaf)
    }

    override operator fun String.div(child: LayoutDirectory): LayoutDirectory {
        val left = chainUnder(container, this, isFile = false, declaredAt = captureDeclarationSite())
        left.leaf.add(child.top)
        return LayoutDirectory(top = left.top, leaf = child.leaf)
    }

    override operator fun String.div(child: LayoutFile): LayoutFile {
        val left = chainUnder(container, this, isFile = false, declaredAt = captureDeclarationSite())
        left.leaf.add(child.top)
        return LayoutFile(top = left.top, leaf = child.leaf)
    }

    override operator fun LayoutDirectory.div(child: String): LayoutDirectory {
        val right = chainUnder(leaf, child, isFile = false, declaredAt = captureDeclarationSite())
        return LayoutDirectory(top = this.top, leaf = right.leaf)
    }

    override operator fun LayoutDirectory.div(child: LayoutDirectory): LayoutDirectory {
        leaf.add(child.top)
        return LayoutDirectory(top = this.top, leaf = child.leaf)
    }

    override operator fun LayoutDirectory.div(child: LayoutFile): LayoutFile {
        leaf.add(child.top)
        return LayoutFile(top = this.top, leaf = child.leaf)
    }

    private fun declareFile(name: String): LayoutFile {
        val chain = chainUnder(container, name, isFile = true, declaredAt = captureDeclarationSite())
        return LayoutFile(top = chain.top, leaf = chain.leaf)
    }
}
