package me.tbsten.katachi.dokka.internal.featured

import org.jetbrains.dokka.DokkaConfiguration.DokkaSourceSet
import org.jetbrains.dokka.model.Documentable
import org.jetbrains.dokka.model.doc.DocTag
import org.jetbrains.dokka.model.doc.Text
import org.jetbrains.dokka.model.properties.ExtraProperty
import org.jetbrains.dokka.model.properties.MergeStrategy
import org.jetbrains.dokka.model.properties.PropertyContainer
import org.jetbrains.dokka.model.withDescendants

/** The KDoc tag that marks a declaration as featured, without its `@`. */
internal const val FEATURED_TAG: String = "featured"

/**
 * Says that a declaration carries `@featured` in its KDoc.
 *
 * [summaryOverride] holds, per source set, the text written after the tag. It is empty when the
 * tag stands alone, and the first paragraph of the KDoc is used instead.
 */
internal data class FeaturedMarker(
    val summaryOverride: Map<DokkaSourceSet, DocTag>,
) : ExtraProperty<Documentable> {
    override val key: ExtraProperty.Key<Documentable, *> = FeaturedMarker

    companion object : ExtraProperty.Key<Documentable, FeaturedMarker> {
        // The marker is attached after the source sets are merged, so this is not expected to
        // run. Dokka's default is to fail, which would be the wrong answer if a later version
        // ever merged again: the union of both is what "tagged somewhere" means.
        override fun mergeStrategyFor(left: FeaturedMarker, right: FeaturedMarker): MergeStrategy<Documentable> =
            MergeStrategy.Replace(FeaturedMarker(left.summaryOverride + right.summaryOverride))
    }
}

/** The marker on [this] container, if the declaration it belongs to is featured. */
internal val PropertyContainer<*>.featuredMarker: FeaturedMarker?
    get() = allOfType<FeaturedMarker>().firstOrNull()

/** Whether [this] holds any text at all; an empty KDoc section parses to tags with no text. */
internal fun DocTag.hasText(): Boolean = withDescendants().any { it is Text && it.body.isNotBlank() }
