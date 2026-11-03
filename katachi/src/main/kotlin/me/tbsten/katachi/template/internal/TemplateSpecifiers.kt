package me.tbsten.katachi.template.internal

import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.LayoutEntry
import me.tbsten.katachi.dsl.Role
import me.tbsten.katachi.dsl.internal.LayoutTemplate
import me.tbsten.katachi.dsl.internal.SpecifierCandidate
import me.tbsten.katachi.dsl.internal.TemplateSetDeclaration
import me.tbsten.katachi.dsl.internal.SpecifierResolution
import me.tbsten.katachi.dsl.internal.resolveSpecifier
import me.tbsten.katachi.dsl.internal.Template
import me.tbsten.katachi.template.KatachiAmbiguousTemplateException
import me.tbsten.katachi.template.KatachiInvalidTemplateSpecifierException
import me.tbsten.katachi.template.KatachiUnknownTemplateException

internal class DeclaredTemplate(
    val role: Role,
    val template: LayoutTemplate,
    val entries: List<LayoutEntry>,
) {
    val id: String? get() = template.id
    val title: String? get() = template.title
    val declaredAt: DeclarationSite get() = template.declaredAt

    val specifier: String = buildString {
        append(role.qualifiedName)
        id?.let { append('.').append(it) }
    }

    override fun toString(): String = "DeclaredTemplate($specifier)"
}

internal fun declaredTemplatesOf(entries: List<LayoutEntry>): List<DeclaredTemplate> {
    val grouped = LinkedHashMap<Pair<Role, LayoutTemplate>, MutableList<LayoutEntry>>()
    for (entry in entries) {
        val template = entry[Template] ?: continue
        grouped.getOrPut(entry.role to template) { mutableListOf() } += entry
    }
    return grouped.map { (key, entries) -> DeclaredTemplate(role = key.first, template = key.second, entries = entries) }
}

// Reads a --arg template= entry the way resolveSpecifier does: whole, then with the last segment as an id.
internal fun resolveTemplate(
    spec: String,
    table: List<DeclaredTemplate>,
    declaredTemplateSets: List<String> = emptyList(),
): DeclaredTemplate {
    val candidates = table.map { SpecifierCandidate(it.role.qualifiedName, it.role.name, it.id) }
    return when (val resolved = resolveSpecifier(spec, candidates)) {
        is SpecifierResolution.Found -> table[resolved.index]
        is SpecifierResolution.Ambiguous -> throw KatachiAmbiguousTemplateException(
            specifier = spec,
            candidates = resolved.specifiers,
        )
        SpecifierResolution.Unknown -> throw KatachiUnknownTemplateException(
            specifier = spec,
            declaredTemplates = table.map { it.specifier }.distinct().sorted(),
            declaredTemplateSets = declaredTemplateSets,
        )
    }
}

internal sealed interface TemplateTarget {
    class Templates(val chosen: List<DeclaredTemplate>) : TemplateTarget

    class Set(val declaration: TemplateSetDeclaration) : TemplateTarget
}

internal fun resolveTemplateTarget(
    requested: List<String>,
    table: List<DeclaredTemplate>,
    sets: List<TemplateSetDeclaration>,
): TemplateTarget {
    val declaredSets = sets.map { it.specifier }.sorted()
    val resolved = requested.map { resolveOne(it, table, sets, declaredSets) }
    val chosenSet = resolved.filterIsInstance<TemplateTarget.Set>().firstOrNull()
    if (chosenSet != null) {
        if (requested.size > 1) {
            throw KatachiInvalidTemplateSpecifierException(
                problem = KatachiInvalidTemplateSpecifierException.Problem.TemplateSetNotAlone,
                specifiers = requested,
            )
        }
        return chosenSet
    }
    val chosen = resolved.filterIsInstance<TemplateTarget.Templates>().flatMap { it.chosen }
    requireNoDuplicateTemplates(requested, chosen)
    return TemplateTarget.Templates(chosen)
}

private fun resolveOne(
    spec: String,
    table: List<DeclaredTemplate>,
    sets: List<TemplateSetDeclaration>,
    declaredSets: List<String>,
): TemplateTarget {
    val bySpecifier = sets.filter { it.specifier == spec }
    val setHits = bySpecifier.ifEmpty { sets.filter { it.name == spec } }
    val templateHit = try {
        resolveTemplate(spec, table, declaredSets)
    } catch (_: KatachiUnknownTemplateException) {
        null
    } catch (ambiguous: KatachiAmbiguousTemplateException) {
        if (setHits.isEmpty()) throw ambiguous
        throw KatachiAmbiguousTemplateException(
            specifier = spec,
            candidates = (ambiguous.candidates + setHits.map { it.specifier }).sorted(),
        )
    }
    return when {
        setHits.size > 1 -> throw KatachiAmbiguousTemplateException(
            specifier = spec,
            candidates = (setHits.map { it.specifier } + listOfNotNull(templateHit?.specifier)).sorted(),
        )
        setHits.size == 1 && templateHit != null -> throw KatachiAmbiguousTemplateException(
            specifier = spec,
            candidates = listOf(setHits.single().specifier, templateHit.specifier).sorted(),
        )
        setHits.size == 1 -> TemplateTarget.Set(setHits.single())
        templateHit != null -> TemplateTarget.Templates(listOf(templateHit))
        else -> throw KatachiUnknownTemplateException(
            specifier = spec,
            declaredTemplates = table.map { it.specifier }.distinct().sorted(),
            declaredTemplateSets = declaredSets,
        )
    }
}

// Splits like StringMapDecoder does a List<String> field: an empty value is an empty list.
internal fun splitTemplateArg(raw: String): List<String> = if (raw.isEmpty()) emptyList() else raw.split(',')

internal fun requireValidSpecifiers(specifiers: List<String>): List<String> {
    if (specifiers.isEmpty()) {
        throw KatachiInvalidTemplateSpecifierException(
            problem = KatachiInvalidTemplateSpecifierException.Problem.Empty,
            specifiers = specifiers,
        )
    }
    if (specifiers.any { it.isEmpty() }) {
        throw KatachiInvalidTemplateSpecifierException(
            problem = KatachiInvalidTemplateSpecifierException.Problem.EmptyElement,
            specifiers = specifiers,
        )
    }
    if (specifiers.toSet().size != specifiers.size) {
        throw KatachiInvalidTemplateSpecifierException(
            problem = KatachiInvalidTemplateSpecifierException.Problem.Duplicate,
            specifiers = specifiers,
        )
    }
    return specifiers
}

internal fun requireNoDuplicateTemplates(requested: List<String>, chosen: List<DeclaredTemplate>) {
    if (chosen.map { it.specifier }.toSet().size == chosen.size) return
    throw KatachiInvalidTemplateSpecifierException(
        problem = KatachiInvalidTemplateSpecifierException.Problem.Duplicate,
        specifiers = requested,
    )
}
