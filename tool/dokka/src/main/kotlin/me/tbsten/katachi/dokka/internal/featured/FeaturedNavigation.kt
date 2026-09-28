package me.tbsten.katachi.dokka.internal.featured

import org.jetbrains.dokka.base.renderers.html.NavigationNode
import org.jetbrains.dokka.base.renderers.html.NavigationNodeIcon
import org.jetbrains.dokka.model.toDisplaySourceSets

/**
 * Builds the "Featured" node of the sidebar, as the first child of a module's navigation root.
 *
 * Only Dokka's navigation model is used: the node and its children are `NavigationNode`s, which
 * Dokka's own `NavigationPage` renders and whose DRIs its location provider resolves, exactly like
 * the package and class nodes next to them. In a multi-module build every module's sidebar is
 * joined under its module node by the templating plugin, so each module shows its own "Featured".
 *
 * The node links to the module page: a navigation node always links somewhere, and the module
 * page is where the node sits in the tree. Its children follow Dokka's sidebar conventions: sorted
 * by name the way Dokka sorts siblings, a function named with `()`, and the icon of its kind.
 */
internal object FeaturedNavigation {
    /** [root] with a node titled [title] listing [entries] put first; [root] itself when empty. */
    fun withFeatured(root: NavigationNode, title: String, entries: List<FeaturedEntry>): NavigationNode {
        if (entries.isEmpty()) return root
        val featured = NavigationNode(
            name = title,
            dri = root.dri,
            sourceSets = root.sourceSets,
            icon = null,
            children = entries.map(::nodeOf).sortedWith(SIBLING_ORDER),
        )
        return root.copy(children = listOf(featured) + root.children)
    }

    private fun nodeOf(entry: FeaturedEntry): NavigationNode = NavigationNode(
        name = if (entry.kind == FeaturedDeclarationKind.Function) "${entry.name}()" else entry.name,
        dri = entry.dri,
        sourceSets = entry.sourceSets.toDisplaySourceSets(),
        icon = iconOf(entry.kind),
        children = emptyList(),
    )

    /**
     * The Kotlin-styled icon Dokka gives a page of [kind]. Dokka also tells abstract classes,
     * exceptions and `var`s apart, which needs the declaration itself.
     * TODO: carry those through FeaturedEntry if a featured declaration ever is one of them.
     */
    private fun iconOf(kind: FeaturedDeclarationKind): NavigationNodeIcon = when (kind) {
        FeaturedDeclarationKind.Class, FeaturedDeclarationKind.Constructor -> NavigationNodeIcon.CLASS_KT
        FeaturedDeclarationKind.Interface -> NavigationNodeIcon.INTERFACE_KT
        FeaturedDeclarationKind.Object -> NavigationNodeIcon.OBJECT
        FeaturedDeclarationKind.Enum, FeaturedDeclarationKind.EnumEntry -> NavigationNodeIcon.ENUM_CLASS_KT
        FeaturedDeclarationKind.Annotation -> NavigationNodeIcon.ANNOTATION_CLASS_KT
        FeaturedDeclarationKind.TypeAlias -> NavigationNodeIcon.TYPEALIAS_KT
        FeaturedDeclarationKind.Function -> NavigationNodeIcon.FUNCTION
        FeaturedDeclarationKind.Property -> NavigationNodeIcon.VAL
    }

    /** Dokka's order for sidebar siblings (its `canonicalAlphabeticalOrder`, which is internal). */
    private val SIBLING_ORDER: Comparator<NavigationNode> =
        compareBy<NavigationNode, String>(String.CASE_INSENSITIVE_ORDER) { it.name }.thenBy { it.name }
}
