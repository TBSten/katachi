package me.tbsten.katachi.dokka.llms

import me.tbsten.katachi.dokka.llms.markdown.DocTagMarkdown
import me.tbsten.katachi.dokka.llms.markdown.DocumentationMarkdown
import org.jetbrains.dokka.DokkaConfiguration.DokkaSourceSet
import org.jetbrains.dokka.links.DRI
import org.jetbrains.dokka.links.PointingToCallableParameters
import org.jetbrains.dokka.model.doc.DocTag
import org.jetbrains.dokka.model.toDisplaySourceSets
import org.jetbrains.dokka.pages.DriResolver

/**
 * Resolves the links of one llms file while it is rendered.
 *
 * [resolver] is the one Dokka hands to `DriLocationResolvableWrite`, so every path comes from
 * Dokka's location provider; [page] picks the HTML page or its Markdown version, and [style]
 * decides what is written for it, seen from [fromDirectory], the directory of the file.
 */
internal class LlmsLinks(
    private val resolver: DriResolver,
    private val style: LlmsLinkStyle,
    private val page: LinkedPage = LinkedPage.Markdown,
    private val fromDirectory: String = "",
) {
    fun of(target: LlmsTarget): String? = of(target.dri, target.sourceSets)

    fun of(dri: DRI, sourceSets: Set<DokkaSourceSet>): String? =
        resolver(dri, sourceSets.toDisplaySourceSets())?.let { if (LinkBase.isAbsolute(it)) it else local(page.pathOf(it)) }

    /** The link to a file of this run at [path], relative to the output root. */
    fun local(path: String): String = style.local(path, fromDirectory)

    /**
     * A Markdown writer whose `[Declaration]` links resolve in the source sets of [target], and
     * whose headings sit below a heading of level [headingDepth].
     */
    fun markdownFor(target: LlmsTarget, headingDepth: Int = 0): DocTagMarkdown = DocTagMarkdown(
        resolve = { dri -> of(dri, target.sourceSets) },
        unresolved = { label, dri -> style.unresolved(label, dri, page) },
        headingOffset = headingDepth,
    )

    fun documentationFor(target: LlmsTarget, headingDepth: Int = 0): DocumentationMarkdown =
        DocumentationMarkdown(markdownFor(target, headingDepth))

    /** [summary] on one line with its links resolved; empty when there is none. */
    fun summary(target: LlmsTarget, summary: DocTag?): String = summary?.let { markdownFor(target).oneLine(it) }.orEmpty()
}

/** What an llms file says for a link, depending on whether its run is the final one. */
internal sealed interface LlmsLinkStyle {
    /** The URL of the file at [path], relative to the run's output root, from [fromDirectory]. */
    fun local(path: String, fromDirectory: String): String

    /** What to write for a link labelled [label] to [dri], which has no page in this run. */
    fun unresolved(label: String, dri: DRI, page: LinkedPage): String

    /** The run writes the files where they are read: links are final, made absolute by [base]. */
    class Final(private val base: LinkBase) : LlmsLinkStyle {
        override fun local(path: String, fromDirectory: String): String = base.link(path, fromDirectory)

        override fun unresolved(label: String, dri: DRI, page: LinkedPage): String = label
    }

    /**
     * A module's run of a multi-module build: the aggregating run finishes the links, knowing
     * the other modules, where each file ends up and the published URL. See [DeferredLinks].
     */
    data object Deferred : LlmsLinkStyle {
        override fun local(path: String, fromDirectory: String): String = DeferredLinks.path(path)

        // A parameter (`[item]` in a function's KDoc) has no page in any module, so there is
        // nothing for the aggregating run to find.
        override fun unresolved(label: String, dri: DRI, page: LinkedPage): String =
            if (dri.target is PointingToCallableParameters) label else DeferredLinks.driLink(label, dri, page)
    }
}

/** A Markdown list item: `- [name](link): summary`, dropping whichever part is missing. */
internal fun listItem(name: String, link: String?, summary: String): String {
    val label = if (link == null) name else "[$name]($link)"
    return if (summary.isEmpty()) "- $label" else "- $label: $summary"
}
