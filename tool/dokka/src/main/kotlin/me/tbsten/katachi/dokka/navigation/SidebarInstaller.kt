package me.tbsten.katachi.dokka.navigation

import me.tbsten.katachi.dokka.internal.featured.FeaturedCollector
import me.tbsten.katachi.dokka.internal.featured.FeaturedNavigation
import me.tbsten.katachi.dokka.internal.featured.htmlFeaturedTitle
import me.tbsten.katachi.dokka.katachiConfiguration
import org.jetbrains.dokka.base.renderers.html.NavigationNode
import org.jetbrains.dokka.base.renderers.html.NavigationPage
import org.jetbrains.dokka.model.DModule
import org.jetbrains.dokka.model.withDescendants
import org.jetbrains.dokka.pages.ModulePage
import org.jetbrains.dokka.pages.RendererSpecificPage
import org.jetbrains.dokka.pages.RootPageNode
import org.jetbrains.dokka.plugability.DokkaContext
import org.jetbrains.dokka.transformers.pages.PageTransformer

/**
 * Builds a module's sidebar: the `NavigationPage` Dokka's `navigationPageInstaller` added, with
 * every change this plugin makes to it.
 *
 * The changes are steps over Dokka's `NavigationNode` tree, applied in order — the "Featured" node
 * put first ([FeaturedNavigation]), then the packages nested by name ([PackageNavigation]) — and
 * the page is replaced once, at the end. One installer rather than one per step, because a step
 * that replaces the page with its own renderer ([LabelledPackageNavigationPage]) would hide it
 * from any later step looking for a `NavigationPage`.
 *
 * The page is Dokka's own `NavigationPage` unless the tree has a label-only node
 * ([isLabelOnly], made only by [PackageNavigation.HierarchicalNoLink]), so it is rendered — and,
 * when template substitution is delayed, wrapped for the templating plugin to join into the
 * aggregated sidebar — exactly as before. The copied renderer is used only where it changes the
 * output, so a module without such a node does not depend on the copy following Dokka. The
 * aggregating run of a multi-module build has no module page and is left as it is: its sidebar is
 * the join of the modules' ones.
 */
internal class SidebarInstaller(private val context: DokkaContext) : PageTransformer {
    private val configuration by lazy { context.katachiConfiguration() }

    override fun invoke(input: RootPageNode): RootPageNode {
        val navigation = input.children.filterIsInstance<NavigationPage>().firstOrNull() ?: return input
        val module = input.withDescendants().filterIsInstance<ModulePage>().firstOrNull()
            ?.documentables?.filterIsInstance<DModule>()?.firstOrNull()
            ?: return input
        val mode = configuration.packageNavigationMode

        val root = listOf<(NavigationNode) -> NavigationNode>(
            { FeaturedNavigation.withFeatured(it, configuration.htmlFeaturedTitle, FeaturedCollector.collect(module)) },
            mode::nest,
        ).fold(navigation.root) { node, step -> step(node) }
        if (root === navigation.root) return input

        val sidebar: RendererSpecificPage = if (root.hasLabelOnly()) {
            LabelledPackageNavigationPage(root, navigation.moduleName, navigation.context)
        } else {
            NavigationPage(root, navigation.moduleName, navigation.context)
        }
        return input.modified(children = input.children.map { if (it === navigation) sidebar else it })
    }

    private fun NavigationNode.hasLabelOnly(): Boolean = isLabelOnly || children.any { it.hasLabelOnly() }
}
