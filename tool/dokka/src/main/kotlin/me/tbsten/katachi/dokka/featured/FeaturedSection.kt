package me.tbsten.katachi.dokka.featured

import org.jetbrains.dokka.DokkaConfiguration.DokkaSourceSet
import org.jetbrains.dokka.base.translators.documentables.PageContentBuilder
import org.jetbrains.dokka.links.DRI
import org.jetbrains.dokka.model.doc.DocTag
import org.jetbrains.dokka.pages.ContentGroup
import org.jetbrains.dokka.pages.ContentKind
import org.jetbrains.dokka.pages.Kind

/** The content kind of the "Featured" section and its table, so it can be found again. */
internal object FeaturedContentKind : Kind {
    override fun toString(): String = "Featured"
}

/** One line of the section: a name linking to [target], and an optional summary. */
internal data class FeaturedRow(
    val name: String,
    val target: FeaturedTarget,
    val summary: FeaturedRowSummary?,
)

/** Where a row links to. */
internal sealed interface FeaturedTarget {
    /** A declaration of this run, resolved by Dokka's location provider when rendered. */
    data class Declaration(val dri: DRI, val sourceSets: Set<DokkaSourceSet>) : FeaturedTarget

    /** A page of another run, already resolved to a path relative to the page shown. */
    data class Path(val path: String) : FeaturedTarget
}

/** The summary of a row, in whichever form the run that shows it still has. */
internal sealed interface FeaturedRowSummary {
    /** The KDoc itself; its first paragraph is shown the way Dokka shows its own briefs. */
    data class Doc(val docTag: DocTag) : FeaturedRowSummary

    /** A summary that already left its run; see [SummarySegment]. */
    data class Segments(val segments: List<SummarySegment>) : FeaturedRowSummary
}

/**
 * Builds the "Featured" section: a level-2 header and a table of the same shape as Dokka's own
 * "Packages" and "All modules" tables, so it is rendered with the same look.
 */
internal object FeaturedSection {
    fun build(
        builder: PageContentBuilder,
        title: String,
        dri: Set<DRI>,
        sourceSets: Set<DokkaSourceSet>,
        rows: List<FeaturedRow>,
    ): ContentGroup = builder.contentFor(dri, sourceSets, kind = FeaturedContentKind) {
        header(2, title, kind = FeaturedContentKind)
        table(kind = FeaturedContentKind) {
            rows.forEach { row ->
                val target = row.target
                val rowDri = if (target is FeaturedTarget.Declaration) setOf(target.dri) else dri
                val rowSourceSets = if (target is FeaturedTarget.Declaration) target.sourceSets else sourceSets
                row(dri = rowDri, sourceSets = rowSourceSets) {
                    when (target) {
                        is FeaturedTarget.Declaration -> link(row.name, target.dri, kind = ContentKind.Main)
                        is FeaturedTarget.Path -> link(row.name, target.path, kind = ContentKind.Main)
                    }
                    when (val summary = row.summary) {
                        is FeaturedRowSummary.Doc -> firstParagraphComment(summary.docTag)
                        is FeaturedRowSummary.Segments -> group(kind = ContentKind.Comment) {
                            summary.segments.forEach { segment ->
                                if (segment.code) codeInline { text(segment.text) } else text(segment.text)
                            }
                        }
                        null -> Unit
                    }
                }
            }
        }
    }
}
