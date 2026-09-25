package me.tbsten.katachi.dokka.featured

import me.tbsten.katachi.dokka.katachiConfiguration
import org.jetbrains.dokka.base.renderers.html.NavigationPage
import org.jetbrains.dokka.model.DModule
import org.jetbrains.dokka.model.withDescendants
import org.jetbrains.dokka.pages.ModulePage
import org.jetbrains.dokka.pages.RootPageNode
import org.jetbrains.dokka.plugability.DokkaContext
import org.jetbrains.dokka.transformers.pages.PageTransformer

/**
 * Puts the "Featured" node at the top of a module's sidebar.
 *
 * Runs as an HTML preprocessor after Dokka's `navigationPageInstaller`, and replaces the
 * `NavigationPage` it added with one whose root has the node of [FeaturedNavigation] first. The
 * page is Dokka's own, so it is rendered — and, when template substitution is delayed, wrapped
 * for the templating plugin to join into the aggregated sidebar — exactly as before.
 *
 * The aggregating run of a multi-module build has no module page and is left as it is: its
 * sidebar is the join of the modules' ones.
 */
internal class FeaturedNavigationInstaller(private val context: DokkaContext) : PageTransformer {
    private val title by lazy { context.katachiConfiguration().featuredTitle }

    override fun invoke(input: RootPageNode): RootPageNode {
        val navigation = input.children.filterIsInstance<NavigationPage>().firstOrNull() ?: return input
        val module = input.withDescendants().filterIsInstance<ModulePage>().firstOrNull()
            ?.documentables?.filterIsInstance<DModule>()?.firstOrNull()
            ?: return input
        val entries = FeaturedCollector.collect(module)
        if (entries.isEmpty()) return input

        val featured = NavigationPage(
            root = FeaturedNavigation.withFeatured(navigation.root, title, entries),
            moduleName = navigation.moduleName,
            context = navigation.context,
        )
        return input.modified(children = input.children.map { if (it === navigation) featured else it })
    }
}
