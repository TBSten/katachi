package me.tbsten.katachi.dokka.navigation

import org.jetbrains.dokka.base.renderers.html.NavigationNode
import org.jetbrains.dokka.links.DRI

/**
 * Re-nests the package nodes of a module's navigation root by the `.`-separated segments of
 * their names.
 *
 * `com.example.data` and `com.example.data.local` become `com.example > data > local`. A segment
 * without a package of its own becomes a node made by the [VirtualNodeFactory]. A virtual segment
 * with a single sub-package is merged into it, like an IDE's "compact middle packages"
 * (`com > example` becomes `com.example`, `domain > model` becomes `domain.model` linking to
 * `com.example.domain.model`). A real package is never merged, so that every package page keeps a
 * node of its own — and the one `pageId` the sidebar script highlights it by.
 *
 * A real package keeps its own node (DRI, source sets, styles, and its class / function
 * children); its sub-packages are put in front of those children, like a file tree.
 *
 * Nodes that are not named packages (Featured, the root package `[root]`, a node another plugin
 * added) keep their place: the ones before the first package stay in front of the tree, the rest
 * follow it, each in their original order.
 */
internal object PackageTree {
    /** Builds the node of [packageName] (e.g. `com.example`), which has no page, labelled [label]. */
    fun interface VirtualNodeFactory {
        fun create(packageName: String, label: String, children: List<NavigationNode>): NavigationNode
    }

    fun nest(root: NavigationNode, virtualNode: VirtualNodeFactory): NavigationNode {
        val firstPackage = root.children.indexOfFirst { it.isNamedPackage(root) }
        if (firstPackage < 0) return root
        val (packages, trailing) = root.children.drop(firstPackage).partition { it.isNamedPackage(root) }
        val trie = Trie()
        packages.forEach { trie.insert(it.dri.packageName.orEmpty(), it) }
        val tree = trie.children.values.map { it.toNode(virtualNode) }.sortedWith(SIBLING_ORDER)
        return root.copy(children = root.children.take(firstPackage) + tree + trailing)
    }

    private class Trie(val qualifiedName: String = "", val segment: String = "") {
        val children = sortedMapOf<String, Trie>()
        var real: NavigationNode? = null

        fun insert(packageName: String, node: NavigationNode) {
            var current = this
            packageName.split('.').forEach { segment ->
                val parent = current
                current = parent.children.getOrPut(segment) {
                    Trie(if (parent.qualifiedName.isEmpty()) segment else "${parent.qualifiedName}.$segment", segment)
                }
            }
            // Dokka merges a package across source sets into one page, so a second node is a bug.
            if (current.real != null) throw KatachiDokkaDuplicatePackageNodeException(packageName)
            current.real = node
        }

        fun toNode(virtualNode: VirtualNodeFactory, labelPrefix: String = ""): NavigationNode {
            val label = labelPrefix + segment
            val real = real
            if (real == null && children.size == 1) return children.values.single().toNode(virtualNode, "$label.")
            val subPackages = children.values.map { it.toNode(virtualNode) }.sortedWith(SIBLING_ORDER)
            if (real == null) return virtualNode.create(qualifiedName, label, subPackages)
            return real.copy(name = label, children = subPackages + real.children)
        }
    }

    /** A package page's node: a DRI with a package and nothing below it, and a non-empty name. */
    private fun NavigationNode.isNamedPackage(root: NavigationNode): Boolean =
        dri != root.dri &&
            dri.classNames == null && dri.callable == null && dri.extra == null &&
            !dri.packageName.isNullOrEmpty() &&
            dri.packageName != ALL_TYPES_PACKAGE

    /** `AllTypesPageNode.DRI.packageName`, which dokka-base does not expose as a constant. */
    private const val ALL_TYPES_PACKAGE = ".alltypes"

    /** Dokka's order for sidebar siblings (its `canonicalAlphabeticalOrder`, which is internal). */
    private val SIBLING_ORDER: Comparator<NavigationNode> =
        compareBy<NavigationNode, String>(String.CASE_INSENSITIVE_ORDER) { it.name }.thenBy { it.name }
}

/** Marks the DRI of a package node without a page, so [LabelledPackageNavigationPage] prints it as text. */
private const val LABEL_ONLY_DRI_EXTRA: String = "me.tbsten.katachi.dokka.label-only-package"

/** The DRI of the node of [packageName], which has no page, for [PackageNavigation.HierarchicalNoLink]. */
internal fun labelOnlyDri(packageName: String): DRI = DRI(packageName = packageName, extra = LABEL_ONLY_DRI_EXTRA)

/** Whether this node was made by [labelOnlyDri]: a package segment without a page. */
internal val NavigationNode.isLabelOnly: Boolean get() = dri.extra == LABEL_ONLY_DRI_EXTRA

/**
 * The navigation tree has two package nodes of [packageName]. Dokka gives a package one page
 * across all source sets, so this is a bug of this plugin or of a plugin that added the node.
 */
internal class KatachiDokkaDuplicatePackageNodeException(
    val packageName: String,
) : IllegalStateException(
    "The sidebar of the API reference has two nodes for the package $packageName, so " +
        "me.tbsten.katachi.dokka.KatachiDokkaPlugin cannot tell which one to nest. Dokka gives a " +
        "package a single page, so this is a bug of :tool:dokka or of another Dokka plugin that adds " +
        "sidebar nodes. Report it with the Dokka plugins in use; setting packageNavigation to " +
        "\"flat\" works around it.",
)
