package me.tbsten.katachi.dsl.internal

import me.tbsten.katachi.dsl.DocumentSection

/** A body as it was written, and the section it was written under. */
internal class DocumentSectionBody(val section: DocumentSection, val markdown: String)

/** The Markdown written under [section], or `null` when this declaration did not write it. */
internal operator fun MetadataValues.get(section: DocumentSection): String? =
    this[section.key]?.markdown

/**
 * The sections one declaration wrote, in the order it wrote them.
 *
 * Found by the shape of the value rather than by a key, because nobody holds the keys: a section
 * is declared by whoever writes the definition, and the page that renders it has never heard of
 * it.
 */
internal fun MetadataValues.documentSectionBodies(): List<DocumentSectionBody> =
    writtenValues().filterIsInstance<DocumentSectionBody>()
