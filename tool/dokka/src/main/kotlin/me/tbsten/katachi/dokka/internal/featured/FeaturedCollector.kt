package me.tbsten.katachi.dokka.internal.featured

import me.tbsten.katachi.dokka.internal.summary.Summaries
import me.tbsten.katachi.dokka.internal.summary.documentationOf
import org.jetbrains.dokka.base.translators.documentables.firstParagraphBrief
import org.jetbrains.dokka.model.DModule
import org.jetbrains.dokka.model.Documentable
import org.jetbrains.dokka.model.doc.DocTag
import org.jetbrains.dokka.model.properties.WithExtraProperties
import org.jetbrains.dokka.model.withDescendants

/**
 * Collects the declarations of a module that [FeaturedTagTransformer] marked.
 *
 * The result is stable from run to run: sorted by package, then name. Overloads live on one page,
 * so declarations of the same package, name and kind are listed once, with the summary of the
 * first one met.
 */
internal object FeaturedCollector {
    fun collect(module: DModule): List<FeaturedEntry> {
        val root: Documentable = module
        return root.withDescendants()
            .mapNotNull { documentable -> entryOf(documentable) }
            .distinctBy { Triple(it.packageName, it.name, it.kind) }
            .sortedWith(compareBy<FeaturedEntry>({ it.packageName }, { it.name }, { it.kind.ordinal }))
            .toList()
    }

    private fun entryOf(documentable: Documentable): FeaturedEntry? {
        val marker = (documentable as? WithExtraProperties<*>)?.extra?.featuredMarker ?: return null
        val kind = FeaturedDeclarationKind.of(documentable) ?: return null
        return FeaturedEntry(
            name = displayNameOf(documentable, kind),
            kind = kind,
            packageName = documentable.dri.packageName.orEmpty(),
            dri = documentable.dri,
            sourceSets = documentable.sourceSets,
            summary = summaryOf(documentable, marker),
        )
    }

    /**
     * `Owner.member` for members, the bare name at the top level. A constructor is shown as its
     * class, which is what Dokka titles its page with. An extension is shown without its
     * receiver. TODO: decide whether `String.module` reads better than `module`.
     */
    private fun displayNameOf(documentable: Documentable, kind: FeaturedDeclarationKind): String {
        val dri = documentable.dri
        val callableName = dri.callable?.name?.takeUnless { kind == FeaturedDeclarationKind.Constructor }
        return listOfNotNull(dri.classNames, callableName).joinToString(".")
            .ifEmpty { documentable.name.orEmpty() }
    }

    /**
     * The text after `@featured` if there is one, taken as written; else the summary every llms
     * file shows (see [Summaries]); else nothing.
     */
    private fun summaryOf(documentable: Documentable, marker: FeaturedMarker): DocTag? {
        val preferred = documentable.expectPresentInSet ?: documentable.sourceSets.firstOrNull()
        return (marker.summaryOverride[preferred] ?: marker.summaryOverride.values.firstOrNull())
            ?.let(::firstParagraphBrief)
            ?: Summaries.of(documentationOf(documentable))
    }
}
