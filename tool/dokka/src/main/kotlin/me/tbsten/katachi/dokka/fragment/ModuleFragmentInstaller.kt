package me.tbsten.katachi.dokka.fragment

import me.tbsten.katachi.dokka.featured.FeaturedCollector
import me.tbsten.katachi.dokka.featured.toSummarySegments
import me.tbsten.katachi.dokka.katachiConfiguration
import me.tbsten.katachi.dokka.llms.documentationOf
import me.tbsten.katachi.dokka.llms.summary.Summaries
import me.tbsten.katachi.dokka.llmsFiles
import org.jetbrains.dokka.base.templating.toJsonString
import org.jetbrains.dokka.model.DModule
import org.jetbrains.dokka.model.toDisplaySourceSets
import org.jetbrains.dokka.pages.DriResolver
import org.jetbrains.dokka.pages.ModulePage
import org.jetbrains.dokka.pages.PageNode
import org.jetbrains.dokka.pages.RendererSpecificResourcePage
import org.jetbrains.dokka.pages.RenderingStrategy
import org.jetbrains.dokka.pages.RootPageNode
import org.jetbrains.dokka.plugability.DokkaContext
import org.jetbrains.dokka.transformers.pages.PageTransformer

/**
 * Leaves [FRAGMENT_FILE] next to a module's output, for the aggregating run to collect.
 *
 * Installed only when template substitution is delayed, which is how a module is generated as
 * part of a multi-module build. The paths are resolved by Dokka's own location provider at
 * render time, through `DriLocationResolvableWrite` — the same mechanism as `package-list`.
 */
internal class ModuleFragmentInstaller(private val context: DokkaContext) : PageTransformer {
    override fun invoke(input: RootPageNode): RootPageNode = input.transformPageNodeTree { page ->
        if (page is ModulePage) page.withChild(fragmentPageOf(page)) else page
    }

    // Typed as PageNode so that only its two-argument `modified` is in scope.
    private fun PageNode.withChild(child: PageNode): PageNode = modified(children = children + child)

    private fun fragmentPageOf(page: ModulePage): RendererSpecificResourcePage {
        val module = page.documentables.filterIsInstance<DModule>().firstOrNull()
        return RendererSpecificResourcePage(
            FRAGMENT_FILE,
            emptyList(),
            RenderingStrategy.DriLocationResolvableWrite { resolver -> toJsonString(fragmentOf(module, resolver)) },
        )
    }

    private fun fragmentOf(module: DModule?, resolver: DriResolver): ModuleFragment = ModuleFragment(
        schemaVersion = FRAGMENT_SCHEMA_VERSION,
        moduleName = module?.name ?: context.configuration.moduleName,
        moduleSummary = module?.summaryText(),
        featured = module?.let { entriesOf(it, resolver) }.orEmpty(),
        // Written by LlmsModuleInstaller next to this file, at the module's output root.
        llmsFiles = context.katachiConfiguration().llmsFiles,
        modulePath = module?.let { resolver(it.dri, it.sourceSets.toDisplaySourceSets()) },
        pageMarkdown = context.katachiConfiguration().pageMarkdown,
    )

    private fun entriesOf(module: DModule, resolver: DriResolver): List<FragmentEntry> =
        FeaturedCollector.collect(module).mapNotNull { entry ->
            val path = resolver(entry.dri, entry.sourceSets.toDisplaySourceSets())
            if (path == null) {
                context.logger.warn(
                    "katachi-dokka: ${entry.name} is tagged @featured, but Dokka could not resolve " +
                        "a page for it, so it is left out of the Featured section of the aggregated llms.txt.",
                )
                return@mapNotNull null
            }
            FragmentEntry(
                name = entry.name,
                kind = entry.kind.label,
                summary = entry.summary?.toSummarySegments().orEmpty(),
                path = path,
            )
        }

    private fun DModule.summaryText(): String? = Summaries.of(documentationOf(this))
        ?.toSummarySegments()
        ?.joinToString("") { if (it.code) "`${it.text}`" else it.text }
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
}
