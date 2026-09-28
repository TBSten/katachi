package me.tbsten.katachi.dokka.internal.link

import org.jetbrains.dokka.base.templating.parseJson
import org.jetbrains.dokka.base.templating.toJsonString
import org.jetbrains.dokka.links.DRI
import java.util.Base64

/**
 * The placeholders a module's run leaves in its llms files for the aggregating run to fill in.
 *
 * A module generated as part of a multi-module build does not know where the aggregated site
 * is published, nor where the other modules' pages are. Dokka's HTML has the same problem and
 * leaves a `ResolveLinkCommand` template for a link to another module; these are the llms files'
 * counterpart, written in text because the files are Markdown, not HTML:
 *
 * - a link to a page of the module itself becomes a [path] token in place of the URL, so that
 *   the aggregating run can make it absolute;
 * - a link the module could not resolve keeps its DRI and its label in a [driLink] element, so
 *   that the aggregating run can resolve it against the other modules, or write the label alone.
 *
 * [rewrite] turns both back into Markdown.
 */
internal object DeferredLinks {
    private const val PATH_PREFIX = "katachi-dokka-path:"
    private const val TAG = "katachi-dokka-link"

    private val PATH = Regex("""${Regex.escape(PATH_PREFIX)}([^)\s]+)""")
    private val DRI_LINK = Regex("""<$TAG dri="([A-Za-z0-9_-]*)"( page="md")?>(.*?)</$TAG>""", RegexOption.DOT_MATCHES_ALL)

    /** The URL to write for a page of the module at [path], relative to the module's output root. */
    fun path(path: String): String = PATH_PREFIX + path

    /**
     * A link labelled [label] to [dri], which the module's own run could not resolve, to be
     * finished as a link to that version of [page].
     */
    fun driLink(label: String, dri: DRI, page: LinkedPage = LinkedPage.Html): String {
        val json = toJsonString(dri).toByteArray(Charsets.UTF_8)
        val pageAttribute = if (page == LinkedPage.Markdown) """ page="md"""" else ""
        return """<$TAG dri="${Base64.getUrlEncoder().withoutPadding().encodeToString(json)}"$pageAttribute>$label</$TAG>"""
    }

    /**
     * [text] with every placeholder replaced: a [path] token by [pathLink] of its path, and a
     * [driLink] by a Markdown link to [resolveDri] of its DRI and page version — or by its label
     * alone, when that is null or the DRI cannot be read. The DRIs left as labels are returned with the text.
     */
    fun rewrite(text: String, pathLink: (String) -> String, resolveDri: (DRI, LinkedPage) -> String?): Rewritten {
        val unresolved = mutableListOf<String>()
        val withDris = DRI_LINK.replace(text) { match ->
            val label = match.groupValues[3]
            val page = if (match.groupValues[2].isEmpty()) LinkedPage.Html else LinkedPage.Markdown
            val dri = decode(match.groupValues[1])
            val link = dri?.let { resolveDri(it, page) }
            if (link == null) {
                unresolved += dri?.toString() ?: "(unreadable) ${match.groupValues[1]}"
                label
            } else {
                markdownLink(label, link)
            }
        }
        val rewritten = PATH.replace(withDris) { match -> pathLink(match.groupValues[1]) }
        return Rewritten(rewritten, unresolved)
    }

    private fun decode(encoded: String): DRI? = try {
        parseJson<DRI>(String(Base64.getUrlDecoder().decode(encoded), Charsets.UTF_8))
    } catch (e: Exception) {
        null
    }

    /** The result of [rewrite]: the Markdown, and the DRIs it could only write as their labels. */
    data class Rewritten(val text: String, val unresolved: List<String>)
}

/** `[label](link)`, or `<link>` when there is no label to show. */
internal fun markdownLink(label: String, link: String): String =
    if (label.isBlank()) "<$link>" else "[$label]($link)"
