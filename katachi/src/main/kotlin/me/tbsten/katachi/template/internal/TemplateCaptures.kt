package me.tbsten.katachi.template.internal

import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.KatachiTemplateParameterConflictException
import me.tbsten.katachi.dsl.KatachiTemplateParameterTypeConflictException
import me.tbsten.katachi.dsl.LayoutEntry
import me.tbsten.katachi.dsl.LayoutEntryKind
import me.tbsten.katachi.dsl.Role
import me.tbsten.katachi.dsl.internal.LayoutCaptures
import me.tbsten.katachi.dsl.internal.ON_EXISTING_ARG
import me.tbsten.katachi.dsl.internal.PathCapture
import me.tbsten.katachi.dsl.internal.TEMPLATE_ARG
import me.tbsten.katachi.dsl.internal.TemplateParameterOrigin
import me.tbsten.katachi.dsl.internal.fillCapturedSegments
import me.tbsten.katachi.dsl.internal.placeholder
import me.tbsten.katachi.template.KatachiInvalidTemplateCaptureValueException
import me.tbsten.katachi.template.TemplateCaptureKind
import me.tbsten.katachi.template.TemplateCapturePreview

internal fun captureSitesOf(entries: List<LayoutEntry>): Map<String, DeclarationSite> {
    val sites = LinkedHashMap<String, DeclarationSite>()
    for (entry in entries) {
        for (variant in entry.captureVariants) {
            for (name in variant.names) sites.putIfAbsent(name, entry.declaredAt)
        }
    }
    return sites
}

internal fun captureNamesOf(entries: List<LayoutEntry>): Set<String> = captureSitesOf(entries).keys

internal fun requireNoConflicts(templates: List<DeclaredTemplate>, values: Map<String, String>) {
    val captureSites = LinkedHashMap<String, DeclarationSite>()
    val ownerOf = LinkedHashMap<String, DeclaredTemplate>()
    for (template in templates) {
        for ((name, site) in captureSitesOf(template.entries)) {
            captureSites.putIfAbsent(name, site)
            ownerOf.putIfAbsent(name, template)
        }
    }

    val paramOrigins = LinkedHashMap<String, Pair<DeclaredTemplate, TemplateParameterOrigin>>()
    for (template in templates) {
        val names = captureSitesOf(template.entries).keys
        val origins = parameterOriginsOnEveryBranch(template.template, template.role.qualifiedName, names, values)
        for ((name, origin) in origins) {
            val existing = paramOrigins[name]
            // Type (label -- declaredWith alone reads "enumParameter()" for every enum, so two
            // different enum types would slip past it) or default differs: design draft section
            // 2, "複数指定のとき" -- "型か既定値が食い違ったら落とす".
            if (existing != null && (existing.second.label != origin.label || existing.second.default != origin.default)) {
                val (firstTemplate, firstOrigin) = existing
                throw KatachiTemplateParameterTypeConflictException(
                    name = name,
                    template = template.specifier,
                    declaredAt = origin.declaredAt,
                    label = origin.label,
                    default = origin.default?.let(::argSpellingOf),
                    conflictsWithTemplate = firstTemplate.specifier,
                    conflictsWithDeclaredAt = firstOrigin.declaredAt,
                    conflictsWithLabel = firstOrigin.label,
                    conflictsWithDefault = firstOrigin.default?.let(::argSpellingOf),
                )
            }
            paramOrigins.putIfAbsent(name, template to origin)
        }
    }

    for ((name, site) in captureSites) {
        val owner = ownerOf.getValue(name)
        val (conflictsWith, parameterSite) = when {
            name == TEMPLATE_ARG -> "GenerateCodeFromTemplate.Args.template" to null
            name == ON_EXISTING_ARG -> "GenerateCodeFromTemplate.Args.onExisting" to null
            else -> paramOrigins[name]?.let { (paramTemplate, origin) ->
                val where = if (paramTemplate === owner) "" else " in template \"${paramTemplate.specifier}\""
                "the template parameter declared with ${origin.declaredWith}$where" to origin.declaredAt
            } ?: continue
        }
        throw KatachiTemplateParameterConflictException(
            role = owner.role.qualifiedName,
            name = name,
            conflictsWith = conflictsWith,
            captureDeclaredAt = site,
            parameterDeclaredAt = parameterSite,
        )
    }
}

internal fun requireValidCaptureValues(role: Role, entries: List<LayoutEntry>, values: Map<String, String>) {
    for ((name, site) in captureSitesOf(entries)) {
        val value = values[name] ?: continue
        val problem = captureValueProblemOf(value) ?: continue
        throw KatachiInvalidTemplateCaptureValueException(
            role = role.qualifiedName,
            name = name,
            value = value,
            problem = problem,
            captureDeclaredAt = site,
        )
    }
}

internal fun requireValidFilledSegments(
    role: Role,
    entry: LayoutEntry,
    variant: LayoutCaptures,
    values: Map<String, String>,
) {
    if (variant.pathCaptures.isEmpty()) return
    val sites = captureSitesOf(listOf(entry))
    // A derived occurrence's value is the conversion, which was never checked on its own: `--`
    // converts to nothing at all under flatCase, and a `.kt` segment would then pass below.
    for (capture in variant.pathCaptures) {
        if (capture.transform == null) continue
        val converted = filledValueOf(capture, values) ?: continue
        val problem = captureValueProblemOf(converted) ?: continue
        throw KatachiInvalidTemplateCaptureValueException(
            role = role.qualifiedName,
            name = capture.name,
            value = converted,
            problem = problem,
            captureDeclaredAt = sites.getValue(capture.name),
        )
    }
    val segments = fillCapturedSegments(entry.path, variant.pathCaptures) { filledValueOf(it, values) }
    for ((segmentIndex, captures) in variant.pathCaptures.groupBy { it.segmentIndex }) {
        val segment = segments.getOrNull(segmentIndex) ?: continue
        val problem = filledSegmentProblemOf(segment) ?: continue
        val capture = captures.last()
        throw KatachiInvalidTemplateCaptureValueException(
            role = role.qualifiedName,
            name = capture.name,
            value = filledValueOf(capture, values) ?: values.getValue(capture.name),
            problem = problem,
            segment = segment,
            captureDeclaredAt = sites.getValue(capture.name),
        )
    }
}

// Windows reserves these in every directory whatever the extension (con.txt opens the console).
private val WINDOWS_RESERVED_NAMES: Set<String> =
    setOf("CON", "PRN", "AUX", "NUL") + (1..9).flatMap { listOf("COM$it", "LPT$it") }

private fun captureValueProblemOf(value: String): KatachiInvalidTemplateCaptureValueException.Problem? = when {
    value.isEmpty() -> KatachiInvalidTemplateCaptureValueException.Problem.Empty
    value.isBlank() -> KatachiInvalidTemplateCaptureValueException.Problem.Blank
    value == "." || value == ".." -> KatachiInvalidTemplateCaptureValueException.Problem.DotSegment
    value.any { it == '/' || it == '\\' } -> KatachiInvalidTemplateCaptureValueException.Problem.Separator
    value.any(::isUncreatableInName) -> KatachiInvalidTemplateCaptureValueException.Problem.UncreatableCharacter
    value.first().isWhitespace() || value.last().isWhitespace() ->
        KatachiInvalidTemplateCaptureValueException.Problem.SurroundingWhitespace
    value.last() == '.' -> KatachiInvalidTemplateCaptureValueException.Problem.TrailingDot
    value.substringBefore('.').uppercase() in WINDOWS_RESERVED_NAMES ->
        KatachiInvalidTemplateCaptureValueException.Problem.ReservedName

    else -> null
}

private fun filledSegmentProblemOf(segment: String): KatachiInvalidTemplateCaptureValueException.Problem? = when {
    segment.isBlank() -> KatachiInvalidTemplateCaptureValueException.Problem.Blank
    segment == "." || segment == ".." -> KatachiInvalidTemplateCaptureValueException.Problem.DotSegment
    segment.first().isWhitespace() || segment.last().isWhitespace() ->
        KatachiInvalidTemplateCaptureValueException.Problem.SurroundingWhitespace
    segment.last() == '.' -> KatachiInvalidTemplateCaptureValueException.Problem.TrailingDot
    segment.substringBefore('.').uppercase() in WINDOWS_RESERVED_NAMES ->
        KatachiInvalidTemplateCaptureValueException.Problem.ReservedName

    else -> null
}

internal const val UNCREATABLE_CHARACTERS: String = "*?[]{}:\"<>|"

internal fun isUncreatableInName(character: Char): Boolean =
    character in UNCREATABLE_CHARACTERS ||
        Character.isISOControl(character) ||
        Character.getType(character).let {
            it == Character.LINE_SEPARATOR.toInt() || it == Character.PARAGRAPH_SEPARATOR.toInt()
        }

internal fun filledValueOf(capture: PathCapture, values: Map<String, String>): String? =
    values[capture.name]?.let { value -> capture.transform?.apply?.invoke(value) ?: value }

internal fun placeholderOf(capture: PathCapture): String = capture.placeholder()

internal fun capturePreviewsOf(entries: List<LayoutEntry>): List<TemplateCapturePreview> {
    // Keyed by what a preview says rather than by the preview: the public class declares no `equals`.
    val previews = LinkedHashMap<List<Any?>, TemplateCapturePreview>()
    fun add(preview: TemplateCapturePreview) {
        val key = listOf(preview.name, preview.kind, preview.pattern, preview.position, preview.wildcardIndex, preview.transform)
        previews.putIfAbsent(key, preview)
    }
    for (entry in entries) {
        if (entry.kind != LayoutEntryKind.File || entry.synthetic) continue
        for (variant in entry.captureVariants) {
            for (capture in variant.pathCaptures) {
                add(
                    TemplateCapturePreview(
                        name = capture.name,
                        kind = TemplateCaptureKind.PathCapture,
                        pattern = entry.path,
                        position = capture.segmentIndex,
                        segment = segmentPatternOf(entry, variant, capture.segmentIndex),
                        wildcardIndex = capture.wildcardIndex,
                        transform = capture.transform?.spelling,
                    ),
                )
            }
        }
    }
    return previews.values.toList()
}

private fun segmentPatternOf(entry: LayoutEntry, variant: LayoutCaptures, segmentIndex: Int): String {
    val onSegment = variant.pathCaptures.filter { it.segmentIndex == segmentIndex }
    return fillCapturedSegments(entry.path, onSegment) { placeholderOf(it) }.getOrNull(segmentIndex)
        ?: entry.path.split('/').getOrNull(segmentIndex).orEmpty()
}

internal fun placeholderOf(name: String): String = "\$" + "{" + name + "}"

internal fun argSpellingOf(value: Any): String = when (value) {
    is Enum<*> -> value.name
    else -> value.toString()
}
