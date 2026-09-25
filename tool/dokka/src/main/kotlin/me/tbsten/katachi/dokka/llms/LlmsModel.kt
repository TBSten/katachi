package me.tbsten.katachi.dokka.llms

import me.tbsten.katachi.dokka.featured.FeaturedEntry
import me.tbsten.katachi.dokka.llms.summary.Summaries
import org.jetbrains.dokka.DokkaConfiguration.DokkaSourceSet
import org.jetbrains.dokka.links.DRI
import org.jetbrains.dokka.model.doc.Description
import org.jetbrains.dokka.model.doc.DocTag
import org.jetbrains.dokka.model.doc.DocumentationNode

/**
 * What the llms files and Markdown pages say, taken from the page tree Dokka is about to render.
 *
 * Everything is still a DRI or a KDoc tree here. Links are resolved and Markdown is written only
 * when the files are rendered, where Dokka's location provider is at hand.
 *
 * A scope is what one directory of the output holds: a module, a package or a type, each of
 * which gets an `llms.txt` and an `llms-full.txt` of its own.
 */
internal sealed interface LlmsScope {
    val name: String
    val target: LlmsTarget
    val documentation: DocumentationNode?

    /** The summary shown under the title and next to links to this scope. */
    val summary: DocTag? get() = Summaries.of(documentation)
}

internal data class LlmsModule(
    override val name: String,
    override val target: LlmsTarget,
    override val documentation: DocumentationNode?,
    val featured: List<FeaturedEntry>,
    val packages: List<LlmsPackage>,
) : LlmsScope

/** A package page and what it lists. [optional] packages go last, under `Optional`. */
internal data class LlmsPackage(
    override val name: String,
    override val target: LlmsTarget,
    override val documentation: DocumentationNode?,
    val optional: Boolean,
    val types: List<LlmsDeclaration>,
    val functions: List<LlmsDeclaration>,
    val properties: List<LlmsDeclaration>,
) : LlmsScope

/**
 * A declaration with a page of its own, or a member listed on its owner's page.
 *
 * [name] is `Owner.Nested` for types and the plain name for callables. [members] and
 * [nestedTypes] are empty except for types: their constructors, enum entries, functions and
 * properties, one per overload, and the types declared directly inside them.
 */
internal data class LlmsDeclaration(
    override val name: String,
    val kind: String,
    override val target: LlmsTarget,
    val signature: String?,
    override val documentation: DocumentationNode?,
    val deprecated: Boolean,
    val members: List<LlmsDeclaration> = emptyList(),
    val nestedTypes: List<LlmsDeclaration> = emptyList(),
) : LlmsScope {
    /** This type and every type nested in it, at any depth, in page order. */
    fun withNestedTypes(): List<LlmsDeclaration> = listOf(this) + nestedTypes.flatMap { it.withNestedTypes() }
}

/** The description of [this] KDoc, without its block tags. */
internal val DocumentationNode?.description: DocTag?
    get() = this?.children?.filterIsInstance<Description>()?.firstOrNull()?.root

/** A page to link to, resolved by Dokka's location provider when the file is rendered. */
internal data class LlmsTarget(val dri: DRI, val sourceSets: Set<DokkaSourceSet>)
