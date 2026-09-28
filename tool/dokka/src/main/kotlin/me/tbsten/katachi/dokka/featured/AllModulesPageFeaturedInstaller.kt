package me.tbsten.katachi.dokka.featured

import me.tbsten.katachi.dokka.KatachiDokkaPlugin
import me.tbsten.katachi.dokka.internal.featured.htmlFeaturedTitle
import me.tbsten.katachi.dokka.katachiConfiguration
import org.jetbrains.dokka.base.DokkaBase
import org.jetbrains.dokka.base.translators.documentables.PageContentBuilder
import org.jetbrains.dokka.pages.ContentGroup
import org.jetbrains.dokka.pages.ContentHeader
import org.jetbrains.dokka.pages.ContentNode
import org.jetbrains.dokka.pages.ContentPage
import org.jetbrains.dokka.pages.MultimoduleRootPage
import org.jetbrains.dokka.pages.RootPageNode
import org.jetbrains.dokka.plugability.DokkaContext
import org.jetbrains.dokka.plugability.plugin
import org.jetbrains.dokka.plugability.querySingle
import org.jetbrains.dokka.transformers.pages.PageTransformer

/**
 * Puts one "Featured" section for all modules on the aggregated top page, above "All modules:".
 *
 * Runs as an HTML preprocessor of the aggregating run, which renders the top page only after
 * [ModuleFragmentStrategy] has read every module's fragment. The page is found by its type,
 * `MultimoduleRootPage`, so the all-modules-page plugin itself is never referenced.
 *
 * The top page lives at the root of the output, so the rebased paths of the fragments are
 * already correct relative links.
 */
internal class AllModulesPageFeaturedInstaller(private val context: DokkaContext) : PageTransformer {
    private val registry by lazy { context.plugin<KatachiDokkaPlugin>().querySingle { moduleFragmentRegistry } }
    private val builder by lazy {
        val base = context.plugin<DokkaBase>()
        PageContentBuilder(
            base.querySingle { commentsToContentConverter },
            base.querySingle { signatureProvider },
            context.logger,
        )
    }

    override fun invoke(input: RootPageNode): RootPageNode =
        input.transformContentPagesTree { page -> if (page is MultimoduleRootPage) withFeatured(page) else page }

    private fun withFeatured(page: ContentPage): ContentPage {
        val rows = registry.fragmentsOf(context.configuration.modules.map { it.name })
            .flatMap { fragment -> fragment.featured }
            .map { entry ->
                FeaturedRow(
                    name = entry.name,
                    target = FeaturedTarget.Path(entry.path),
                    summary = entry.summary.takeIf { it.isNotEmpty() }?.let { FeaturedRowSummary.Segments(it) },
                )
            }
        if (rows.isEmpty()) return page
        val content = page.content as? ContentGroup ?: return page

        val section = FeaturedSection.build(
            builder = builder,
            title = context.katachiConfiguration().htmlFeaturedTitle,
            dri = page.dri,
            sourceSets = emptySet(),
            rows = rows,
        )
        return page.modified(content = content.copy(children = content.children.inserting(section)))
    }

    /**
     * [section] placed before the first header of the page, which is "All modules:". The
     * project's own documentation, if any, comes before it inside a group of its own, so it stays
     * on top.
     */
    private fun List<ContentNode>.inserting(section: ContentNode): List<ContentNode> {
        val header = indexOfFirst { it is ContentHeader }.takeIf { it >= 0 } ?: 0
        return take(header) + section + drop(header)
    }
}
