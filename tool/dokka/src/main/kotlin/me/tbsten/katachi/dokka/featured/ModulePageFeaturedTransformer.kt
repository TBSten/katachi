package me.tbsten.katachi.dokka.featured

import me.tbsten.katachi.dokka.internal.featured.FeaturedCollector
import me.tbsten.katachi.dokka.internal.featured.htmlFeaturedTitle
import me.tbsten.katachi.dokka.katachiConfiguration
import org.jetbrains.dokka.base.DokkaBase
import org.jetbrains.dokka.base.translators.documentables.PageContentBuilder
import org.jetbrains.dokka.model.DModule
import org.jetbrains.dokka.pages.ContentGroup
import org.jetbrains.dokka.pages.ContentKind
import org.jetbrains.dokka.pages.ContentNode
import org.jetbrains.dokka.pages.ContentPage
import org.jetbrains.dokka.pages.ModulePageNode
import org.jetbrains.dokka.pages.RootPageNode
import org.jetbrains.dokka.plugability.DokkaContext
import org.jetbrains.dokka.plugability.plugin
import org.jetbrains.dokka.plugability.querySingle
import org.jetbrains.dokka.transformers.pages.PageTransformer

/**
 * Puts the "Featured" section of a module on its module page, right above "Packages".
 *
 * The links are `ContentDRILink`s, left for Dokka's location provider to resolve like any other
 * link on the page. A module without a `@featured` declaration keeps its page as it was.
 */
internal class ModulePageFeaturedTransformer(private val context: DokkaContext) : PageTransformer {
    private val builder by lazy {
        val base = context.plugin<DokkaBase>()
        PageContentBuilder(
            base.querySingle { commentsToContentConverter },
            base.querySingle { signatureProvider },
            context.logger,
        )
    }
    private val title by lazy { context.katachiConfiguration().htmlFeaturedTitle }

    override fun invoke(input: RootPageNode): RootPageNode =
        input.transformContentPagesTree { page -> if (page is ModulePageNode) withFeatured(page) else page }

    private fun withFeatured(page: ModulePageNode): ContentPage {
        val module = page.documentables.filterIsInstance<DModule>().firstOrNull() ?: return page
        val entries = FeaturedCollector.collect(module)
        if (entries.isEmpty()) return page
        val content = page.content as? ContentGroup ?: return page

        val section = FeaturedSection.build(
            builder = builder,
            title = title,
            dri = page.dri,
            sourceSets = module.sourceSets,
            rows = entries.map { entry ->
                FeaturedRow(
                    name = entry.name,
                    target = FeaturedTarget.Declaration(entry.dri, entry.sourceSets),
                    summary = entry.summary?.let { FeaturedRowSummary.Doc(it) },
                )
            },
        )
        return page.modified(content = content.copy(children = content.children.inserting(section)))
    }

    /** [section] placed before "Packages", or after the cover when the page has no packages. */
    private fun List<ContentNode>.inserting(section: ContentNode): List<ContentNode> {
        val packages = indexOfFirst { it.dci.kind == ContentKind.Packages }
        val index = when {
            packages >= 0 -> packages
            else -> indexOfFirst { it.dci.kind == ContentKind.Cover } + 1
        }
        return take(index) + section + drop(index)
    }
}
