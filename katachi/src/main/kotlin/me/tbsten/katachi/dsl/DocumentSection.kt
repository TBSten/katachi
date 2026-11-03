package me.tbsten.katachi.dsl

import kotlin.reflect.KProperty
import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.internal.DocumentSectionBody
import me.tbsten.katachi.dsl.internal.captureDeclarationSite
import me.tbsten.katachi.dsl.internal.metadataBuilder

/**
 * A section of the generated documentation, declared by whoever writes the definition.
 *
 * Use it for what a team wants said about every group and role beyond katachi's own sections
 * ("how this layer is tested", "who owns it"): one [documentSection] per heading, and a property
 * to write it through. The value is Markdown, placed exactly as written. A section nobody wrote
 * is absent.
 *
 * ## Nesting
 *
 * A section below another one is declared on the section it sits below. Its heading level follows
 * from how deep it sits: `##` at the top, one more `#` per step down. A parent nobody wrote still
 * gets its heading when something below it was written.
 *
 * A section carries no default and inherits nothing: a role inside a group says its own or says
 * nothing (see [MetadataScope]).
 *
 * ## Example 1: declare a section and write it on a group
 * ```kt
 * val TestPolicy = documentSection("テスト方針")
 * var DeclarationContainerScope.testPolicy by TestPolicy
 *
 * val arch = architecture {
 *     "api".group {
 *         testPolicy = "- ステータスコード"
 *     }
 * }
 * arch.groups.single()[TestPolicy] shouldBe "- ステータスコード"
 * ```
 *
 * ## Example 2: a section below another one, written on a role
 * ```kt
 * val TestPolicy = documentSection("テスト方針")
 * val UnitTest = TestPolicy.documentSection("ユニットテスト")
 * var MetadataScope.unitTest by UnitTest
 *
 * val arch = architecture {
 *     "api".group {
 *         "Controller".role { unitTest = "リクエストの変換だけを見る。" }
 *     }
 * }
 * arch.allRoles.single()[UnitTest] shouldBe "リクエストの変換だけを見る。"
 * ```
 *
 * @see documentSection
 * @see MetadataKey
 */
@ExperimentalKatachiApi
public class DocumentSection internal constructor(
    /** The heading this section is written out under, without the leading `#`s. */
    public val heading: String,
    /** The section this one sits below, or `null` when it is a top level one. */
    public val parent: DocumentSection?,
) {
    internal val key: MetadataKey<DocumentSectionBody> = metadata(DocumentSectionBody::class)

    internal val depth: Int = if (parent == null) 0 else parent.depth + 1

    /**
     * Reads the Markdown written under this section, so that a section can be the delegate of a
     * property.
     */
    public operator fun getValue(thisRef: MetadataScope, property: KProperty<*>): String? =
        thisRef.metadataBuilder()[key]?.markdown

    /**
     * Writes [value] under this section.
     *
     * Writing `null` leaves the declaration exactly as it would have been without the line.
     */
    public operator fun setValue(thisRef: MetadataScope, property: KProperty<*>, value: String?) {
        thisRef.metadataBuilder()[key] = value?.let { DocumentSectionBody(section = this, markdown = it) }
    }

    /**
     * Declares a section below this one, whose heading is written one level deeper.
     *
     * @throws KatachiUnnamedDocumentSectionException when [heading] is blank.
     * @throws KatachiDocumentSectionTooDeepException when the section would be written below the
     *   deepest heading Markdown has.
     */
    public fun documentSection(heading: String): DocumentSection =
        declareDocumentSection(heading = heading, parent = this)

    /** `DocumentSection(<heading>)`. */
    override fun toString(): String = "DocumentSection($heading)"
}

/**
 * Declares a top level section of the generated documentation, written out as `## <heading>`.
 *
 * Hold it in one top level `val` shared by the property writing it and every reader: two
 * `documentSection` calls with the same heading are two different sections.
 *
 * ## Example 1: declare a section and the property it is written through
 * ```kt
 * val TestPolicy = documentSection("テスト方針")
 * var DeclarationContainerScope.testPolicy by TestPolicy
 * ```
 *
 * @throws KatachiUnnamedDocumentSectionException when [heading] is blank.
 * @see DocumentSection
 */
@ExperimentalKatachiApi
public fun documentSection(heading: String): DocumentSection =
    declareDocumentSection(heading = heading, parent = null)

// `##` plus this many steps reaches `######`, the last Markdown heading level.
private const val MAX_SECTION_DEPTH: Int = 4

private fun declareDocumentSection(heading: String, parent: DocumentSection?): DocumentSection {
    if (heading.isBlank()) throw KatachiUnnamedDocumentSectionException(captureDeclarationSite())
    val depth = if (parent == null) 0 else parent.depth + 1
    if (depth > MAX_SECTION_DEPTH) {
        throw KatachiDocumentSectionTooDeepException(
            heading = heading,
            depth = depth,
            maxDepth = MAX_SECTION_DEPTH,
            declaredAt = captureDeclarationSite(),
        )
    }
    return DocumentSection(heading = heading, parent = parent)
}
