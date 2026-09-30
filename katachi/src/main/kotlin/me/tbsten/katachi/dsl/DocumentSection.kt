package me.tbsten.katachi.dsl

import kotlin.reflect.KProperty
import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.internal.DocumentSectionBody
import me.tbsten.katachi.dsl.internal.captureDeclarationSite
import me.tbsten.katachi.dsl.internal.metadataBuilder

/**
 * A section of the generated documentation, declared by whoever writes the definition.
 *
 * katachi ships the sections it knows how to fill — the placement table, the named constraints,
 * the examples — and stops there. What else a team wants said about every group and every role
 * ("how this layer is tested", "who owns it") is theirs, so it is declared rather than picked
 * from a list katachi guessed at: one [documentSection] per heading, and a property to write it
 * through.
 *
 * The value is Markdown and is placed exactly as written, like [Description]: no heading is
 * shifted and no paragraph is rewrapped. A section nobody wrote a body for is absent, the same
 * way the constraint section is absent from a role that named no constraint.
 *
 * ## Nesting
 *
 * A section below another one is declared on the section it sits below, so the parent is the
 * section itself rather than a heading spelled twice. The heading level follows from how deep it
 * sits — `##` at the top, one more `#` per step down — which is why a section may be moved
 * without its heading disagreeing with where it ended up. A parent nobody wrote a body for still
 * gets its heading when something below it was written, because otherwise the child would read
 * as belonging to whatever section came before.
 *
 * A section carries no default and inherits nothing: what a group says is the group's, and a role
 * inside it says its own or says nothing. That is the rule all metadata follows — see
 * [MetadataScope].
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
 *         "Controller" { unitTest = "リクエストの変換だけを見る。" }
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
    /**
     * Where a body written under this section is kept.
     *
     * One key per section, so two sections that happen to share a heading stay two sections —
     * the identity rule [MetadataKey] already follows. The stored value carries the section back,
     * which is what lets a page find the sections of a declaration without anyone keeping a
     * registry of every section ever declared.
     */
    internal val key: MetadataKey<DocumentSectionBody> = metadata(DocumentSectionBody::class)

    /** How many sections this one sits below. `0` is the top level, written as `##`. */
    internal val depth: Int = if (parent == null) 0 else parent.depth + 1

    /**
     * Reads the Markdown written under this section, so that a section can be the delegate of a
     * property. A member rather than an extension, for the reason [MetadataKey.getValue] gives.
     *
     * ## Example 1: give a section a property to be written through
     * ```kt
     * val TestPolicy = documentSection("テスト方針")
     * var DeclarationContainerScope.testPolicy by TestPolicy
     * ```
     */
    public operator fun getValue(thisRef: MetadataScope, property: KProperty<*>): String? =
        thisRef.metadataBuilder()[key]?.markdown

    /**
     * Writes [value] under this section. Writing `null` leaves the declaration exactly as it
     * would have been without the line.
     *
     * ## Example 1: write a section from the DSL through its property
     * ```kt
     * val TestPolicy = documentSection("テスト方針")
     * var DeclarationContainerScope.testPolicy by TestPolicy
     *
     * val arch = architecture {
     *     "api".group { testPolicy = "- ステータスコード" }
     * }
     * arch.groups.single()[TestPolicy] shouldBe "- ステータスコード"
     * ```
     */
    public operator fun setValue(thisRef: MetadataScope, property: KProperty<*>, value: String?) {
        thisRef.metadataBuilder()[key] = value?.let { DocumentSectionBody(section = this, markdown = it) }
    }

    /**
     * Declares a section below this one, whose heading is written one level deeper.
     *
     * ## Example 1: declare a section below another one
     * ```kt
     * val TestPolicy = documentSection("テスト方針")
     * val UnitTest = TestPolicy.documentSection("ユニットテスト")
     *
     * UnitTest.parent shouldBe TestPolicy
     * ```
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
 * Hold it in one top level `val` that the property writing it and every reader share: two
 * sections are the same section only when they are the same object, so two `documentSection`
 * calls with one heading between them are two sections that happen to look alike.
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

/**
 * The deepest a section may sit: `##` plus [MAX_SECTION_DEPTH] steps is `######`, the last
 * heading Markdown has. A `#######` is not a heading at all, it is that text.
 */
private const val MAX_SECTION_DEPTH: Int = 4

/** Validates the heading and the depth, then builds the section. */
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
