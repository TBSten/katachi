package me.tbsten.katachi.dokka.navigation

import kotlinx.html.DIV
import kotlinx.html.FlowContent
import kotlinx.html.TagConsumer
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.id
import kotlinx.html.span
import kotlinx.html.stream.createHTML
import org.jetbrains.dokka.base.renderers.html.HtmlRenderer
import org.jetbrains.dokka.base.renderers.html.NavigationNode
import org.jetbrains.dokka.base.renderers.html.NavigationNodeIcon
import org.jetbrains.dokka.base.renderers.html.buildBreakableText
import org.jetbrains.dokka.base.renderers.html.strike
import org.jetbrains.dokka.base.renderers.html.templateCommand
import org.jetbrains.dokka.base.templating.AddToNavigationCommand
import org.jetbrains.dokka.model.DisplaySourceSet
import org.jetbrains.dokka.pages.PageNode
import org.jetbrains.dokka.pages.RendererSpecificPage
import org.jetbrains.dokka.pages.RenderingStrategy
import org.jetbrains.dokka.pages.TextStyle
import org.jetbrains.dokka.plugability.DokkaContext

/**
 * The sidebar of [PackageNavigation.HierarchicalNoLink]: Dokka's `NavigationPage`, except that a
 * package node without a page ([isLabelOnly]) is printed as a label instead of a link.
 *
 * `NavigationPage` is final and renders privately, so its rendering is copied here from
 * dokka-base 2.2.0: `org.jetbrains.dokka.base.renderers.html.NavigationPage` (the two `visit`s
 * and `nodeText`), plus two internals it relies on — `NavigationNode.pageId` of
 * `org.jetbrains.dokka.base.renderers.pageId.kt`, and the CSS classes of
 * `NavigationNodeIcon.style()`. Everything but the label is emitted as Dokka does.
 *
 * `LabelledPackageNavigationPageSpec` in the tests renders one tree without labels with both this
 * page and `NavigationPage`, with and without delayed template substitution, and expects the same
 * text, so an upgrade of Dokka that changes any of those fails there. Carry the change over from
 * the three sources above: the `toc--*` classes and nesting, the `id` / `pageId` /
 * `data-nesting-level` attributes that `navigation-loader.js` reads, the `AddToNavigationCommand`
 * wrapper the templating plugin joins a multi-module sidebar by, the `pageId` format (the script
 * matches it against the page's `#content[pageIds]` to highlight the current page), and new
 * `NavigationNodeIcon` entries (the `when` below stops compiling on one).
 *
 * `SidebarInstaller` uses this page only when the tree has a label, so a module without one is
 * rendered by Dokka itself.
 */
internal class LabelledPackageNavigationPage(
    val root: NavigationNode,
    val moduleName: String,
    val context: DokkaContext,
) : RendererSpecificPage {
    override val name: String = "navigation"
    override val children: List<PageNode> = emptyList()
    override fun modified(name: String, children: List<PageNode>): PageNode = this

    override val strategy: RenderingStrategy = RenderingStrategy<HtmlRenderer> {
        createHTML().render(this)
    }

    private fun <R> TagConsumer<R>.render(renderer: HtmlRenderer): R {
        val navId = "$moduleName-nav-submenu"
        return if (context.configuration.delayTemplateSubstitution) {
            templateCommand(AddToNavigationCommand(moduleName)) {
                div("toc--part") { part(root, navId, renderer, 0) }
            }
        } else {
            div("toc--part") { part(root, navId, renderer, 0) }
        }
    }

    private fun DIV.part(node: NavigationNode, navId: String, renderer: HtmlRenderer, level: Int) {
        id = navId
        attributes["pageId"] = "$moduleName::${pageIdOf(node)}"
        attributes["data-nesting-level"] = level.toString()
        div("toc--row") {
            if (node.children.isNotEmpty()) {
                button(classes = "toc--button") {
                    attributes["aria-expanded"] = "false"
                    attributes["aria-label"] = node.name
                    attributes["onclick"] = "window.handleTocButtonClick(event, '$navId')"
                }
            }
            if (node.isLabelOnly) {
                // No page exists for this package, so the label toggles the node instead of
                // linking. `toc--link` is kept for Dokka's per-level indentation and hover style,
                // which its stylesheet spells out for each nesting level. A side effect:
                // navigation-loader.js prefixes the `href` of every `.toc--link` with the path to
                // the root, so in the browser this span gets `href="<path>null"`. It is inert on a
                // span, but a check that collects `[href]` from the live DOM has to skip
                // `.toc--label-only`.
                span("toc--link toc--label-only") {
                    attributes["style"] = "cursor: pointer"
                    attributes["onclick"] = "window.handleTocButtonClick(event, '$navId')"
                    attributes["title"] = node.dri.packageName.orEmpty()
                    nodeText(node)
                }
            } else {
                link(node, renderer)
                if (node.children.isNotEmpty()) {
                    a {
                        attributes["class"] = "toc--skip-link"
                        attributes["href"] = ""
                        +"Skip to content"
                    }
                }
            }
        }
        node.children.forEachIndexed { n, child ->
            div("toc--part") { part(child, "$navId-$n", renderer, level + 1) }
        }
    }

    private fun FlowContent.link(node: NavigationNode, renderer: HtmlRenderer) = with(renderer) {
        buildLink(node.dri, node.sourceSets.toList()) {
            this@buildLink.attributes["class"] = "toc--link"
            val icon = node.icon
            if (icon != null) {
                // Keeps the icon on the left when a long name wraps, as Dokka does.
                span("toc--link-grid") {
                    span("toc--icon ${cssClassOf(icon)}")
                    span { nodeText(node) }
                }
            } else {
                nodeText(node)
            }
        }
    }

    private fun FlowContent.nodeText(node: NavigationNode) {
        if (node.styles.contains(TextStyle.Strikethrough)) {
            strike(classes = "strikethrough") { buildBreakableText(node.name) }
        } else {
            buildBreakableText(node.name)
        }
    }

    private fun pageIdOf(node: NavigationNode): String = "${node.dri}/${hashOf(node.sourceSets)}"

    private fun hashOf(sourceSets: Set<DisplaySourceSet>): Int =
        sourceSets.sortedBy { it.sourceSetIDs.merged.let { id -> id.scopeId + id.sourceSetName } }
            .joinToString().hashCode()

    private fun cssClassOf(icon: NavigationNodeIcon): String = when (icon) {
        NavigationNodeIcon.CLASS -> "class"
        NavigationNodeIcon.CLASS_KT -> "class-kt"
        NavigationNodeIcon.ABSTRACT_CLASS -> "abstract-class"
        NavigationNodeIcon.ABSTRACT_CLASS_KT -> "abstract-class-kt"
        NavigationNodeIcon.ENUM_CLASS -> "enum-class"
        NavigationNodeIcon.ENUM_CLASS_KT -> "enum-class-kt"
        NavigationNodeIcon.ANNOTATION_CLASS -> "annotation-class"
        NavigationNodeIcon.ANNOTATION_CLASS_KT -> "annotation-class-kt"
        NavigationNodeIcon.INTERFACE -> "interface"
        NavigationNodeIcon.INTERFACE_KT -> "interface-kt"
        NavigationNodeIcon.FUNCTION -> "function"
        NavigationNodeIcon.EXCEPTION -> "exception-class"
        NavigationNodeIcon.OBJECT -> "object"
        NavigationNodeIcon.TYPEALIAS_KT -> "typealias-kt"
        NavigationNodeIcon.VAL -> "val"
        NavigationNodeIcon.VAR -> "var"
    }
}
