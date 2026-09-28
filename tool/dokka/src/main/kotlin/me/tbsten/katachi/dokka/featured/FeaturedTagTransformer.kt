package me.tbsten.katachi.dokka.featured

import me.tbsten.katachi.dokka.internal.featured.FEATURED_TAG
import me.tbsten.katachi.dokka.internal.featured.FeaturedMarker
import me.tbsten.katachi.dokka.internal.featured.hasText
import org.jetbrains.dokka.DokkaConfiguration.DokkaSourceSet
import org.jetbrains.dokka.model.DAnnotation
import org.jetbrains.dokka.model.DClass
import org.jetbrains.dokka.model.DClasslike
import org.jetbrains.dokka.model.DEnum
import org.jetbrains.dokka.model.DEnumEntry
import org.jetbrains.dokka.model.DFunction
import org.jetbrains.dokka.model.DInterface
import org.jetbrains.dokka.model.DModule
import org.jetbrains.dokka.model.DObject
import org.jetbrains.dokka.model.DPackage
import org.jetbrains.dokka.model.DProperty
import org.jetbrains.dokka.model.Documentable
import org.jetbrains.dokka.model.doc.CustomTagWrapper
import org.jetbrains.dokka.model.properties.WithExtraProperties
import org.jetbrains.dokka.plugability.DokkaContext
import org.jetbrains.dokka.transformers.documentation.DocumentableTransformer

/**
 * Attaches a [FeaturedMarker] to every declaration whose KDoc carries `@featured`.
 *
 * Registered after the source sets are merged, so only what survived Dokka's visibility filter is
 * seen: a tag on an `internal` declaration is dropped together with the declaration.
 * TODO: warn about such tags, which needs a transformer that runs before the filter.
 */
internal class FeaturedTagTransformer(
    @Suppress("unused") private val context: DokkaContext,
) : DocumentableTransformer {
    override fun invoke(original: DModule, context: DokkaContext): DModule =
        original.copy(packages = original.packages.map { it.transform() })

    private fun DPackage.transform(): DPackage = copy(
        functions = functions.map { it.transform() },
        properties = properties.map { it.transform() },
        classlikes = classlikes.map { it.transform() },
        typealiases = typealiases.map { it.marked() },
    )

    private fun DClasslike.transform(): DClasslike = when (this) {
        is DClass -> copy(
            constructors = constructors.map { it.transform() },
            functions = functions.map { it.transform() },
            properties = properties.map { it.transform() },
            classlikes = classlikes.map { it.transform() },
            typealiases = typealiases.map { it.marked() },
            companion = companion?.transformObject(),
        ).marked()

        is DInterface -> copy(
            functions = functions.map { it.transform() },
            properties = properties.map { it.transform() },
            classlikes = classlikes.map { it.transform() },
            typealiases = typealiases.map { it.marked() },
            companion = companion?.transformObject(),
        ).marked()

        is DObject -> transformObject()

        is DEnum -> copy(
            entries = entries.map { it.transform() },
            constructors = constructors.map { it.transform() },
            functions = functions.map { it.transform() },
            properties = properties.map { it.transform() },
            classlikes = classlikes.map { it.transform() },
            typealiases = typealiases.map { it.marked() },
            companion = companion?.transformObject(),
        ).marked()

        is DAnnotation -> copy(
            constructors = constructors.map { it.transform() },
            functions = functions.map { it.transform() },
            properties = properties.map { it.transform() },
            classlikes = classlikes.map { it.transform() },
            companion = companion?.transformObject(),
        ).marked()
    }

    private fun DObject.transformObject(): DObject = copy(
        functions = functions.map { it.transform() },
        properties = properties.map { it.transform() },
        classlikes = classlikes.map { it.transform() },
        typealiases = typealiases.map { it.marked() },
    ).marked()

    private fun DEnumEntry.transform(): DEnumEntry = copy(
        functions = functions.map { it.transform() },
        properties = properties.map { it.transform() },
        classlikes = classlikes.map { it.transform() },
    ).marked()

    private fun DFunction.transform(): DFunction = marked()

    private fun DProperty.transform(): DProperty = marked()
}

/** [this] with a [FeaturedMarker] added, or unchanged when its KDoc has no `@featured`. */
private fun <T> T.marked(): T where T : Documentable, T : WithExtraProperties<T> =
    featuredMarkerOf(this)?.let { withNewExtras(extra + it) } ?: this

private fun featuredMarkerOf(documentable: Documentable): FeaturedMarker? {
    val tags: Map<DokkaSourceSet, CustomTagWrapper> = documentable.documentation
        .mapNotNull { (sourceSet, node) ->
            node.children.filterIsInstance<CustomTagWrapper>()
                .firstOrNull { it.name == FEATURED_TAG }
                ?.let { sourceSet to it }
        }
        .toMap()
    if (tags.isEmpty()) return null
    return FeaturedMarker(
        summaryOverride = tags
            .filterValues { it.root.hasText() }
            .mapValues { (_, tag) -> tag.root },
    )
}
