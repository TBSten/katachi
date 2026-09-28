package me.tbsten.katachi.dokka.llms

import me.tbsten.katachi.dokka.KatachiDokkaPlugin
import me.tbsten.katachi.dokka.internal.fragment.FragmentEntry
import me.tbsten.katachi.dokka.internal.fragment.ModuleFragment
import me.tbsten.katachi.dokka.internal.link.LinkBase
import me.tbsten.katachi.dokka.internal.link.directoryOf
import me.tbsten.katachi.dokka.internal.link.listItem
import me.tbsten.katachi.dokka.internal.link.markdownPathOf
import me.tbsten.katachi.dokka.katachiConfiguration
import org.jetbrains.dokka.base.DokkaBase
import org.jetbrains.dokka.model.withDescendants
import org.jetbrains.dokka.pages.MultimoduleRootPage
import org.jetbrains.dokka.pages.RendererSpecificResourcePage
import org.jetbrains.dokka.pages.RenderingStrategy
import org.jetbrains.dokka.pages.RootPageNode
import org.jetbrains.dokka.plugability.DokkaContext
import org.jetbrains.dokka.plugability.plugin
import org.jetbrains.dokka.plugability.querySingle
import org.jetbrains.dokka.transformers.pages.PageTransformer

/**
 * Puts an `llms.txt` at the root of the aggregated output — the featured declarations of every
 * module, and a link to each module's own llms files — and the Markdown version of the top page,
 * which lists the modules.
 *
 * Runs as an HTML preprocessor of the aggregating run, after every module's fragment has been
 * read (see `ModuleFragmentStrategy`). Everything it lists comes from those fragments, whose
 * paths are already relative to the aggregated output root, where `llms.txt` is written. Where the
 * top page is, and so its Markdown version, comes from Dokka's location provider.
 */
internal class LlmsIndexInstaller(private val context: DokkaContext) : PageTransformer {
    private val registry by lazy { context.plugin<KatachiDokkaPlugin>().querySingle { moduleFragmentRegistry } }
    private val configuration by lazy { context.katachiConfiguration() }

    override fun invoke(input: RootPageNode): RootPageNode {
        val topPage = input.withDescendants().filterIsInstance<MultimoduleRootPage>().firstOrNull() ?: return input
        val fragments = registry.fragmentsOf(context.configuration.modules.map { it.name })
        val writer = LlmsIndexWriter(
            title = "${context.configuration.moduleName} API reference",
            summary = configuration.projectSummary,
            featuredTitle = configuration.featuredTitle,
            base = LinkBase(configuration.baseUrl),
        )
        val topPagePath = context.plugin<DokkaBase>().querySingle { locationProviderFactory }
            .getLocationProvider(input).resolve(topPage, null, false)
        val files = listOfNotNull(
            RendererSpecificResourcePage(LLMS_FILE, emptyList(), RenderingStrategy.Write(writer.write(fragments)))
                .takeIf { configuration.llms },
            topPagePath?.takeIf { configuration.pageMarkdown }?.let { path ->
                val markdown = markdownPathOf(path)
                RendererSpecificResourcePage(markdown, emptyList(), RenderingStrategy.Write(writer.topPage(fragments, directoryOf(markdown))))
            },
        )
        // The root the aggregating run renders is a wrapper whose `modified` keeps its children.
        return input.modified(name = input.name, children = input.children + files)
    }
}

/** Writes the aggregated `llms.txt` and the Markdown version of the top page from the modules' fragments. */
internal class LlmsIndexWriter(
    private val title: String,
    private val summary: String?,
    private val featuredTitle: String,
    private val base: LinkBase,
) {
    fun write(fragments: List<ModuleFragment>): String = buildString {
        head()
        section(featuredTitle, featuredItems(fragments, fromDirectory = ""))
        val modules = fragments.flatMap { fragment ->
            fragment.llmsFiles.map { path ->
                val full = path.substringAfterLast('/') == LLMS_FULL_FILE
                listItem(
                    name = if (full) "${fragment.moduleName} (full)" else fragment.moduleName,
                    link = base.link(path),
                    summary = if (full) {
                        "Every declaration of ${fragment.moduleName} with its signature and KDoc."
                    } else {
                        fragment.moduleSummary.orEmpty()
                    },
                )
            }
        }
        section("Modules", modules)
    }

    /** The top page, which lists the modules, as Markdown written in [fromDirectory]. */
    fun topPage(fragments: List<ModuleFragment>, fromDirectory: String): String = buildString {
        head()
        section(featuredTitle, featuredItems(fragments, fromDirectory))
        section(
            "Modules",
            fragments.map { fragment ->
                val page = fragment.modulePath?.let { path -> pageOf(fragment, path) }
                listItem(fragment.moduleName, page?.let { base.link(it, fromDirectory) }, fragment.moduleSummary.orEmpty())
            },
        )
    }

    private fun StringBuilder.head() {
        append("# ").append(title).append('\n')
        summary?.takeIf { it.isNotBlank() }?.let { append("\n> ").append(it.trim()).append('\n') }
    }

    /** Every module's featured declarations, linking to the Markdown version of their pages. */
    private fun featuredItems(fragments: List<ModuleFragment>, fromDirectory: String): List<String> =
        fragments.flatMap { fragment -> fragment.featured.map { fragment to it } }.map { (fragment, entry) ->
            listItem(entry.name, base.link(pageOf(fragment, entry.path), fromDirectory), summaryOf(entry))
        }

    private fun pageOf(fragment: ModuleFragment, path: String): String =
        if (fragment.pageMarkdown) markdownPathOf(path) else path

    private fun summaryOf(entry: FragmentEntry): String =
        entry.summary.joinToString("") { if (it.code) "`${it.text}`" else it.text }.trim()

    private fun StringBuilder.section(title: String, items: List<String>) {
        if (items.isEmpty()) return
        append("\n## ").append(title).append("\n\n")
        items.forEach { append(it).append('\n') }
    }
}
