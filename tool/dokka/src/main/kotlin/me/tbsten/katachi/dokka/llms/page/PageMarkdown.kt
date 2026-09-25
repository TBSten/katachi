package me.tbsten.katachi.dokka.llms.page

import me.tbsten.katachi.dokka.featured.FeaturedDeclarationKind
import me.tbsten.katachi.dokka.featured.FeaturedEntry
import me.tbsten.katachi.dokka.llms.LlmsDeclaration
import me.tbsten.katachi.dokka.llms.LlmsLinks
import me.tbsten.katachi.dokka.llms.LlmsModule
import me.tbsten.katachi.dokka.llms.LlmsPackage
import me.tbsten.katachi.dokka.llms.LlmsScope
import me.tbsten.katachi.dokka.llms.LlmsTxtWriter
import me.tbsten.katachi.dokka.llms.summary.Summaries

/**
 * The Markdown version of one HTML page: a module, a package, a type or a member.
 *
 * [scope] is what the page is about; [declarations] are the declarations whose signature and KDoc
 * the page shows — the type itself, or every overload of a member — and are empty for a module
 * or a package, which only has its documentation. [children] are what the page lists, one entry
 * per page below it.
 */
internal data class PageMarkdown(
    val scope: LlmsScope,
    val declarations: List<LlmsDeclaration>,
    val parent: LlmsScope?,
    val children: List<LlmsScope>,
    val featured: List<FeaturedEntry>,
)

/**
 * Writes a [PageMarkdown]: the name as the title, the summary as a quote, a line saying what it
 * is and where it belongs, the signatures and KDoc, then what the page lists, each with a link to
 * its own Markdown version and its summary.
 */
internal class PageMarkdownWriter(private val featuredTitle: String) {
    fun write(page: PageMarkdown, links: LlmsLinks): String {
        val scope = page.scope
        val blocks = buildList {
            add("# ${scope.name}")
            val summary = links.summary(scope.target, Summaries.firstOf(page.pageDocumentations()))
            if (summary.isNotEmpty()) add("> $summary")
            add(kindLine(page, links))
            if (page.declarations.isEmpty()) {
                add(links.documentationFor(scope.target, headingDepth = 1).of(scope.documentation))
            } else {
                page.declarations.forEachIndexed { index, declaration ->
                    if (index > 0) add("---")
                    addAll(declarationBlocks(declaration, links))
                }
            }
            val featured = LlmsTxtWriter.featuredItems(page.featured, links)
            if (featured.isNotEmpty()) add("## $featuredTitle\n\n" + featured.joinToString("\n"))
            ChildSection.entries.forEach { section ->
                val items = LlmsTxtWriter.items(page.children.filter { section.contains(it) }, links)
                if (items.isNotEmpty()) add("## ${section.title}\n\n" + items.joinToString("\n"))
            }
        }
        return blocks.filter { it.isNotBlank() }.joinToString("\n\n") + "\n"
    }

    private fun PageMarkdown.pageDocumentations() =
        if (declarations.isEmpty()) listOf(scope.documentation) else declarations.map { it.documentation }

    private fun kindLine(page: PageMarkdown, links: LlmsLinks): String {
        val parent = page.parent ?: return "*${kindOf(page.scope)}*"
        val link = links.of(parent.target)
        val parentName = if (link == null) parent.name else "[${parent.name}]($link)"
        return "*${kindOf(page.scope)}* in ${kindOf(parent)} $parentName"
    }

    private fun declarationBlocks(declaration: LlmsDeclaration, links: LlmsLinks): List<String> = listOfNotNull(
        declaration.signature?.let { "```kotlin\n$it\n```" },
        "**Deprecated**".takeIf { declaration.deprecated },
        links.documentationFor(declaration.target, headingDepth = 1).of(declaration.documentation),
    )

    private fun kindOf(scope: LlmsScope): String = when (scope) {
        is LlmsModule -> "module"
        is LlmsPackage -> "package"
        is LlmsDeclaration -> scope.kind
    }
}

/** The groups a page's children are listed in, in the order they are listed. */
private enum class ChildSection(val title: String, private val kinds: Set<FeaturedDeclarationKind>?) {
    Packages("Packages", emptySet()),
    Constructors("Constructors", setOf(FeaturedDeclarationKind.Constructor)),
    Entries("Entries", setOf(FeaturedDeclarationKind.EnumEntry)),
    Types("Types", null),
    Functions("Functions", setOf(FeaturedDeclarationKind.Function)),
    Properties("Properties", setOf(FeaturedDeclarationKind.Property)),
    ;

    fun contains(child: LlmsScope): Boolean = when {
        child is LlmsPackage -> this == Packages
        child !is LlmsDeclaration -> false
        kinds == null -> entries.none { it.kinds?.any { kind -> kind.label == child.kind } == true }
        else -> kinds.any { it.label == child.kind }
    }
}
