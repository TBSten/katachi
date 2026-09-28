package me.tbsten.katachi.dokka.internal.featured

import me.tbsten.katachi.dokka.KatachiDokkaConfiguration
import org.jetbrains.dokka.DokkaConfiguration.DokkaSourceSet
import org.jetbrains.dokka.links.DRI
import org.jetbrains.dokka.model.DAnnotation
import org.jetbrains.dokka.model.DClass
import org.jetbrains.dokka.model.DEnum
import org.jetbrains.dokka.model.DEnumEntry
import org.jetbrains.dokka.model.DFunction
import org.jetbrains.dokka.model.DInterface
import org.jetbrains.dokka.model.DObject
import org.jetbrains.dokka.model.DProperty
import org.jetbrains.dokka.model.DTypeAlias
import org.jetbrains.dokka.model.Documentable
import org.jetbrains.dokka.model.doc.DocTag

/**
 * [KatachiDokkaConfiguration.featuredTitle] as shown in HTML: with a star in front, so it stands
 * out among the plain package and class nodes next to it in the sidebar and on module pages.
 *
 * The llms files and the per-page Markdown keep the plain [KatachiDokkaConfiguration.featuredTitle]
 * as their heading instead, because that heading is machine-facing.
 */
internal val KatachiDokkaConfiguration.htmlFeaturedTitle: String
    get() = "⭐️ $featuredTitle"

/**
 * One `@featured` declaration of a module, as the sidebar, the llms files and the fragment need it.
 *
 * [summary] is the paragraph shown next to it: the text after the tag, or the summary of
 * `me.tbsten.katachi.dokka.internal.summary.Summaries`.
 */
internal data class FeaturedEntry(
    val name: String,
    val kind: FeaturedDeclarationKind,
    val packageName: String,
    val dri: DRI,
    val sourceSets: Set<DokkaSourceSet>,
    val summary: DocTag?,
)

/** What a declaration is, in words a reader of the list would use. Also used by the llms files. */
internal enum class FeaturedDeclarationKind(val label: String) {
    Class("class"),
    Interface("interface"),
    Object("object"),
    Enum("enum"),
    EnumEntry("enum entry"),
    Annotation("annotation"),
    TypeAlias("typealias"),
    Constructor("constructor"),
    Function("function"),
    Property("property"),
    ;

    companion object {
        /** The kind of [documentable], or null for packages, modules and anything else not listed. */
        fun of(documentable: Documentable): FeaturedDeclarationKind? = when (documentable) {
            is DClass -> Class
            is DInterface -> Interface
            is DObject -> Object
            is DEnum -> Enum
            is DEnumEntry -> EnumEntry
            is DAnnotation -> Annotation
            is DTypeAlias -> TypeAlias
            is DFunction -> if (documentable.isConstructor) Constructor else Function
            is DProperty -> Property
            // Packages and modules are not listed. TODO: decide whether a featured package makes sense.
            else -> null
        }
    }
}
