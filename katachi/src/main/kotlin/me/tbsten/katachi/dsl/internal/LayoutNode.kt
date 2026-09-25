package me.tbsten.katachi.dsl.internal

import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.KatachiEmptyLayoutChainException
import me.tbsten.katachi.dsl.KatachiGlobSyntaxException
import me.tbsten.katachi.dsl.LayoutDirectory
import me.tbsten.katachi.dsl.LayoutFile

/**
 * One node of the tree a `layout { }` block builds while it is evaluated.
 *
 * The tree is short-lived: it exists between "run the deferred block" and "flatten it into
 * [me.tbsten.katachi.dsl.LayoutEntry] values", and nothing outside the DSL ever sees it.
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
        private set
    val children: MutableList<LayoutNode> = mutableListOf()

    var description: String? = null

    /** Set by `optional()`. Only read for a file. */
    var optional: Boolean = false

    /** Set by `ignore()`, in either of its two spellings. */
    var ignored: Boolean = false

    /** Set by `anyFile()`. */
    var anyFile: Boolean = false

    /**
     * Whether katachi wrote this node rather than the user: the two lines every `module { }`
     * block injects, and the Groovy `build.gradle` that `gradle()` declares beside them.
     *
     * It changes nothing about the check — `build/` is ignored and `build.gradle.kts` is
     * required whoever declared them — and everything about what a constraint covers. A
     * `":feature:*".module { constraint { } }` whose set of files silently included every
     * feature module's build script would report that build script to a reader who never
     * mentioned it, and a module with no source file yet would pass on the build script
     * alone. Someone who does mean to constrain a build script writes
     * `"build.gradle.kts".file()` themselves, in a block of their own.
     */
    var synthetic: Boolean = false

    /**
     * Whether this node is one of the places a role may live in — a directory a
     * `"...".module { }` key opened for a module other than the root project, and nothing else.
     *
     * A role living in more than one place is asked to say which files belong in which, and the
     * only spelling that has somewhere to write that answer is a block: `description = "..."` is
     * written inside one, so a path that was never opened as a block could be warned about and
     * never fixed. That is why the mark is put on here rather than worked out later from the
     * flattened paths — by then a `/` chain and a block look the same.
     *
     * Only `module { }` keys carry it, although a plain `"core/domain" { }` block directly under
     * `layout { }` reads like a place too. The reason is that katachi's own Gradle vocabulary is
     * written in terms of plain directory blocks — `mainSourceSet` *is* `"src/main" { }`, `kotlin`
     * *is* `"kotlin" { }` — evaluated in whatever scope they are read in, so marking every
     * directory block would mark `src/main` and `src/main/kotlin` inside a `":".module { }` and
     * call one role's single home three separate places. Telling a user's key from the
     * vocabulary's own needs a distinction the DSL does not have yet; until it does, the narrower
     * rule reports nothing it cannot explain.
     */
    var place: Boolean = false

    /**
     * The Gradle module path of the `module { }` block that opened this node, or `null`.
     *
     * Only the directory a module key opened carries it, and for the root project the nodes
     * that key declared straight into the layout root. Flattening hands it down the subtree, so
     * an entry can say which module it belongs to without the path being parsed back.
     */
    var modulePath: String? = null

    /**
     * Whether this node is one of the levels a module package resolved to.
     *
     * A module package is a strategy, not a path, so by the time a node exists the strategy's
     * answer has already been spelled out level by level. Documentation wants the strategy
     * back, and the mark is the only trace of it left.
     */
    var modulePackage: Boolean = false

    fun add(child: LayoutNode) {
        child.parent?.children?.remove(child)
        child.parent = this
        children += child
    }

    /** Marks every file at or below this node optional. See [me.tbsten.katachi.dsl.LayoutModule.optional]. */
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

/** Marks every node a `module { }` default declared. See [LayoutNode.synthetic]. */
internal fun LayoutDirectory.markSynthetic(): LayoutDirectory = also { markSynthetic(top, leaf) }

/** Marks every node a `module { }` default declared. See [LayoutNode.synthetic]. */
internal fun LayoutFile.markSynthetic(): LayoutFile = also { markSynthetic(top, leaf) }

/** Marks every level a module package resolved to. See [LayoutNode.modulePackage]. */
internal fun LayoutDirectory.markModulePackage(): LayoutDirectory = also {
    var current: LayoutNode? = leaf
    while (current != null) {
        current.modulePackage = true
        if (current === top) return@also
        current = current.parent
    }
}

/**
 * Walks [leaf] up to [top] inclusive.
 *
 * A key may spell out several levels, so the whole chain it created is katachi's, not only the
 * node the value points at. Both defaults are one level, which makes this a loop of one today
 * and correct if a default ever gains a level.
 */
private fun markSynthetic(top: LayoutNode, leaf: LayoutNode) {
    var current: LayoutNode? = leaf
    while (current != null) {
        current.synthetic = true
        if (current === top) return
        current = current.parent
    }
}

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
