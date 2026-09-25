package me.tbsten.katachi.dokka.llms

import me.tbsten.katachi.dokka.featured.FeaturedCollector
import me.tbsten.katachi.dokka.featured.FeaturedDeclarationKind
import me.tbsten.katachi.dokka.llms.markdown.SignatureText
import org.jetbrains.dokka.base.signatures.SignatureProvider
import org.jetbrains.dokka.links.DRI
import org.jetbrains.dokka.model.Annotations
import org.jetbrains.dokka.model.DEnumEntry
import org.jetbrains.dokka.model.DFunction
import org.jetbrains.dokka.model.DModule
import org.jetbrains.dokka.model.DPackage
import org.jetbrains.dokka.model.DProperty
import org.jetbrains.dokka.model.Documentable
import org.jetbrains.dokka.model.doc.DocumentationNode
import org.jetbrains.dokka.model.properties.WithExtraProperties
import org.jetbrains.dokka.pages.ClasslikePageNode
import org.jetbrains.dokka.pages.MemberPageNode
import org.jetbrains.dokka.pages.ModulePage
import org.jetbrains.dokka.pages.PackagePageNode

/**
 * Builds the [LlmsScope]s of the module, package and type pages.
 *
 * The pages decide what is listed — so what Dokka left out of the site (private declarations,
 * inherited members) is left out of the llms files too — and the documentables give the KDoc.
 * Signatures come from Dokka's own `SignatureProvider`, the same one that drew them on the pages.
 */
internal class LlmsModelFactory(
    private val signatureProvider: SignatureProvider,
    private val optionalPackages: List<Regex>,
) {
    fun of(page: ModulePage): LlmsModule? {
        val module = page.documentables.filterIsInstance<DModule>().firstOrNull() ?: return null
        return LlmsModule(
            name = module.name,
            target = targetOf(module),
            documentation = documentationOf(module),
            featured = FeaturedCollector.collect(module),
            // A stable sort: Dokka's order is kept within the regular and the optional packages.
            packages = page.children.filterIsInstance<PackagePageNode>().mapNotNull(::of).sortedBy { it.optional },
        )
    }

    fun of(page: PackagePageNode): LlmsPackage? {
        val documentable = page.documentables.filterIsInstance<DPackage>().firstOrNull() ?: return null
        val callables = page.children.filterIsInstance<MemberPageNode>().flatMap { it.documentables }
        return LlmsPackage(
            name = documentable.name,
            target = targetOf(documentable),
            documentation = documentationOf(documentable),
            optional = optionalPackages.any { it.matches(documentable.name) },
            types = page.children.filterIsInstance<ClasslikePageNode>()
                .filter { it.documentables.firstOrNull() !is DEnumEntry }
                .mapNotNull(::of)
                .flatMap { it.withNestedTypes() },
            functions = callables.filterIsInstance<DFunction>().mapNotNull(::declarationOf),
            properties = callables.filterIsInstance<DProperty>().mapNotNull(::declarationOf),
        )
    }

    /** The type of [page] with its members, and the types nested in it; an enum entry has no members. */
    fun of(page: ClasslikePageNode): LlmsDeclaration? {
        val documentable = page.documentables.firstOrNull() ?: return null
        val type = declarationOf(documentable) ?: return null
        val nestedPages = page.children.filterIsInstance<ClasslikePageNode>()
        val (entryPages, typePages) = nestedPages.partition { it.documentables.firstOrNull() is DEnumEntry }
        // Constructors have member pages of their own, so they are among the member pages.
        val members = entryPages.flatMap { it.documentables } +
            page.children.filterIsInstance<MemberPageNode>().flatMap { it.documentables }
        return type.copy(
            members = members.mapNotNull(::declarationOf),
            nestedTypes = typePages.mapNotNull(::of),
        )
    }

    fun declarationOf(documentable: Documentable): LlmsDeclaration? {
        val kind = FeaturedDeclarationKind.of(documentable) ?: return null
        val dri = documentable.dri
        return LlmsDeclaration(
            name = when (kind) {
                FeaturedDeclarationKind.Function, FeaturedDeclarationKind.Property -> dri.callable?.name
                FeaturedDeclarationKind.Constructor -> dri.classNames?.substringAfterLast('.')
                else -> dri.classNames
            } ?: documentable.name.orEmpty(),
            kind = kind.label,
            target = targetOf(documentable),
            signature = SignatureText.of(signatureProvider.signature(documentable)),
            documentation = documentationOf(documentable),
            deprecated = isDeprecated(documentable),
        )
    }

    private fun targetOf(documentable: Documentable) = LlmsTarget(documentable.dri, documentable.sourceSets)

    private fun isDeprecated(documentable: Documentable): Boolean =
        (documentable as? WithExtraProperties<*>)?.extra?.allOfType<Annotations>().orEmpty()
            .flatMap { it.directAnnotations.values.flatten() }
            .any { it.dri in DEPRECATED }

    private companion object {
        val DEPRECATED = setOf(DRI("kotlin", "Deprecated"), DRI("java.lang", "Deprecated"))
    }
}

/** The KDoc of the source set Dokka shows first: the `expect` one, else the first. */
internal fun documentationOf(documentable: Documentable): DocumentationNode? {
    val preferred = documentable.expectPresentInSet ?: documentable.sourceSets.firstOrNull()
    return documentable.documentation[preferred] ?: documentable.documentation.values.firstOrNull()
}
