package me.tbsten.katachi.dsl.internal

import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.KatachiEmptyLayoutChainException
import me.tbsten.katachi.dsl.KatachiGlobSyntaxException
import me.tbsten.katachi.dsl.LayoutDirectory
import me.tbsten.katachi.dsl.LayoutFile

internal class LayoutNode(
    val segment: String,
    val declaredAt: DeclarationSite,
    val isFile: Boolean,
) {
    var parent: LayoutNode? = null
        private set
    val children: MutableList<LayoutNode> = mutableListOf()

    var description: String? = null

    var optional: Boolean = false

    var ignored: Boolean = false

    var anyFile: Boolean = false

    // Does not change what is checked, only what a constraint covers: module-injected lines must not leak into a
    // constraint's file set.
    var synthetic: Boolean = false

    // Only a module-key directory (non-root): a path never opened as a block has nowhere to write `description =
    // ...`.
    var place: Boolean = false

    // Flattening hands it down the subtree so an entry knows its module without parsing the path back.
    var modulePath: String? = null

    // segment already has tokens replaced by `*`; this is the way back to which `*` a `--arg` fills and what a
    // bound name is read by.
    var captureSegment: CaptureSegment? = null

    // An entry is null when only some of the key's `*`s were named.
    var moduleCaptureNames: List<String?>? = null

    var moduleCapturePattern: String? = null

    // Identity shared by nodes of one wildcard module key's expansion; tells replays of one source line from two
    // separate declarations.
    var moduleExpansionGroup: Any? = null

    // Several `.metadata { }` calls may write into this builder before flattening builds it.
    val metadata: MetadataBuilder = MetadataBuilder()

    // A second `.template { }` on this node, kept to report KatachiDuplicateTemplateException once the role is
    // known (see toEntry).
    var duplicateTemplateAttempt: LayoutTemplate? = null

    fun add(child: LayoutNode) {
        child.parent?.children?.remove(child)
        child.parent = this
        children += child
        // A child never overrides a group it already carries (it may be a whole chain built
        // elsewhere and re-parented here, e.g. `"a" / capture("x") { }`'s block) -- it only
        // picks up this node's, so a subtree entirely inside one module wildcard expansion
        // stays tagged with that expansion's identity all the way down.
        child.moduleExpansionGroup = child.moduleExpansionGroup ?: moduleExpansionGroup
    }

    fun markFilesOptional() {
        if (isFile) optional = true
        children.forEach { it.markFilesOptional() }
    }

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

internal class Chain(val top: LayoutNode, val leaf: LayoutNode)

internal fun LayoutDirectory.markSynthetic(): LayoutDirectory = also { markSynthetic(top, leaf) }

internal fun LayoutFile.markSynthetic(): LayoutFile = also { markSynthetic(top, leaf) }

private fun markSynthetic(top: LayoutNode, leaf: LayoutNode) {
    var current: LayoutNode? = leaf
    while (current != null) {
        current.synthetic = true
        if (current === top) return
        current = current.parent
    }
}

// Reject empty levels here, where the user's key is still at hand, for a better message than the joined path.
private fun splitKey(key: String): List<String> {
    val segments = key.split('/')
    if (segments.any { it.isEmpty() }) {
        throw KatachiGlobSyntaxException(key, GlobProblem.EmptyLayoutKeyLevel)
    }
    return segments
}

internal fun chainUnder(
    parent: LayoutNode,
    key: String,
    isFile: Boolean,
    declaredAt: DeclarationSite,
): Chain {
    val segments = splitKey(key)
    var current = parent
    var top: LayoutNode? = null
    segments.forEachIndexed { index, rawSegment ->
        val captureSegment = parseCaptureSegment(rawSegment, declaredAt)
        // Only the last level of a file key is the file itself; the levels above it are the
        // directories that hold it.
        val node = LayoutNode(
            segment = captureSegment?.globSegment ?: rawSegment,
            declaredAt = declaredAt,
            isFile = isFile && index == segments.lastIndex,
        )
        node.captureSegment = captureSegment
        current.add(node)
        if (top == null) top = node
        current = node
    }
    return Chain(top = top ?: throw KatachiEmptyLayoutChainException(key), leaf = current)
}
