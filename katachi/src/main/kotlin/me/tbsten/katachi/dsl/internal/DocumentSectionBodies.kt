package me.tbsten.katachi.dsl.internal

import me.tbsten.katachi.dsl.DocumentSection

internal class DocumentSectionBody(val section: DocumentSection, val markdown: String)

internal operator fun MetadataValues.get(section: DocumentSection): String? =
    this[section.key]?.markdown

// Found by the shape of the value, not by a key: sections are declared by the definition's author and the renderer
// holds no keys.
internal fun MetadataValues.documentSectionBodies(): List<DocumentSectionBody> =
    writtenValues().filterIsInstance<DocumentSectionBody>()
