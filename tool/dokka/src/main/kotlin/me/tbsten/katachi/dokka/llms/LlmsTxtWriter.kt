package me.tbsten.katachi.dokka.llms

import me.tbsten.katachi.dokka.featured.FeaturedDeclarationKind
import me.tbsten.katachi.dokka.featured.FeaturedEntry

/**
 * Writes the `llms.txt` of a module, a package or a type, in the format of https://llmstxt.org/:
 * a title, a summary quote, and sections of links with a one-line summary each.
 *
 * Every link leads to the Markdown version of a page. The sections list what the directory holds:
 * - a module: its featured declarations, then the packages, types, top-level functions and
 *   top-level properties, and last `Optional` for the packages matched by the configuration;
 * - a package: its types, top-level functions and top-level properties;
 * - a type: its constructors, entries, nested types, functions and properties.
 *
 * An empty section is left out. Overloads share a page, so they are listed once.
 */
internal class LlmsTxtWriter(private val featuredTitle: String) {
    fun write(scope: LlmsScope, links: LlmsLinks): String {
        val sections = when (scope) {
            is LlmsModule -> moduleSections(scope, links)
            is LlmsPackage -> packageSections(scope, links)
            is LlmsDeclaration -> typeSections(scope, links)
        }
        return buildString {
            append(header(scope, links))
            sections.filter { (_, items) -> items.isNotEmpty() }.forEach { (title, items) ->
                append("\n## ").append(title).append("\n\n")
                items.forEach { append(it).append('\n') }
            }
        }
    }

    private fun moduleSections(module: LlmsModule, links: LlmsLinks): List<Pair<String, List<String>>> {
        val (optional, regular) = module.packages.partition { it.optional }
        return listOf(
            featuredTitle to featuredItems(module.featured, links),
            "Packages" to regular.map { item(it, links) },
            "Types" to items(regular.flatMap { it.types }, links),
            "Functions" to items(regular.flatMap { it.functions }, links),
            "Properties" to items(regular.flatMap { it.properties }, links),
            "Optional" to optional.flatMap { pkg ->
                listOf(item(pkg, links)) + items(pkg.types + pkg.functions + pkg.properties, links)
            },
        )
    }

    private fun packageSections(pkg: LlmsPackage, links: LlmsLinks): List<Pair<String, List<String>>> = listOf(
        "Types" to items(pkg.types, links),
        "Functions" to items(pkg.functions, links),
        "Properties" to items(pkg.properties, links),
    )

    private fun typeSections(type: LlmsDeclaration, links: LlmsLinks): List<Pair<String, List<String>>> =
        MemberSection.entries.map { section -> section.title to items(section.of(type), links) }

    companion object {
        /** One item per page: overloads share one, with the summary of the first overload that has one. */
        fun items(scopes: List<LlmsScope>, links: LlmsLinks): List<String> = scopes
            .map { it to links.of(it.target) }
            .groupBy { (scope, link) -> link ?: scope.name }
            .map { (_, overloads) ->
                val (first, link) = overloads.first()
                val summary = overloads.map { (scope, _) -> summaryOf(scope, links) }.firstOrNull { it.isNotEmpty() }
                listItem(first.name, link, summary.orEmpty())
            }

        /** `# name` and, when there is one, the summary as a quote. */
        fun header(scope: LlmsScope, links: LlmsLinks): String {
            val summary = links.summary(scope.target, scope.summary)
            return if (summary.isEmpty()) "# ${scope.name}\n" else "# ${scope.name}\n\n> $summary\n"
        }

        fun featuredItems(featured: List<FeaturedEntry>, links: LlmsLinks): List<String> = featured.map { entry ->
            val target = LlmsTarget(entry.dri, entry.sourceSets)
            listItem(entry.name, links.of(target), links.summary(target, entry.summary))
        }

        /** A link to [scope] with its summary. */
        fun item(scope: LlmsScope, links: LlmsLinks): String =
            listItem(scope.name, links.of(scope.target), summaryOf(scope, links))

        /** The summary of [scope] on one line, flagged when deprecated. */
        fun summaryOf(scope: LlmsScope, links: LlmsLinks): String {
            val summary = links.summary(scope.target, scope.summary)
            return if (scope is LlmsDeclaration && scope.deprecated) "**Deprecated** $summary".trim() else summary
        }
    }
}

/** The groups a type's members are listed in, in the order they are listed. */
internal enum class MemberSection(val title: String, private val kind: FeaturedDeclarationKind?) {
    Constructors("Constructors", FeaturedDeclarationKind.Constructor),
    Entries("Entries", FeaturedDeclarationKind.EnumEntry),
    Types("Types", null),
    Functions("Functions", FeaturedDeclarationKind.Function),
    Properties("Properties", FeaturedDeclarationKind.Property),
    ;

    /** The declarations of [type] in this group: nested types at any depth for [Types]. */
    fun of(type: LlmsDeclaration): List<LlmsDeclaration> =
        if (kind == null) type.nestedTypes.flatMap { it.withNestedTypes() } else type.members.filter { it.kind == kind.label }
}
