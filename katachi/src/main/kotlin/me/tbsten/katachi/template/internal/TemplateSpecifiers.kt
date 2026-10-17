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

/**
 * One `.template { }` declaration, gathered across every [LayoutEntry] it was attached to.
 *
 * A wildcard module key's expansion runs the same `.template { }` call once per module it
 * matches, which builds a fresh [LayoutTemplate] instance per module -- but [LayoutTemplate.equals]
 * reads that as one template, and [entries] is every one of those expansions, in declaration
 * order. Everywhere else, [entries] holds exactly one entry.
 */
internal class DeclaredTemplate(
    val role: Role,
    val template: LayoutTemplate,
    val entries: List<LayoutEntry>,
) {
    val id: String? get() = template.id
    val title: String? get() = template.title
    val declaredAt: DeclarationSite get() = template.declaredAt

    /**
     * The complete specifier `--arg template=` accepts for this template: [Role.qualifiedName],
     * with `.id` appended when [id] is not `null`.
     */
    val specifier: String = buildString {
        append(role.qualifiedName)
        id?.let { append('.').append(it) }
    }

    override fun toString(): String = "DeclaredTemplate($specifier)"
}

/**
 * Every `.template { }` of [entries], one [DeclaredTemplate] per distinct declaration -- a
 * wildcard module key's expansion counted once -- in declaration order.
 */
internal fun declaredTemplatesOf(entries: List<LayoutEntry>): List<DeclaredTemplate> {
    val grouped = LinkedHashMap<Pair<Role, LayoutTemplate>, MutableList<LayoutEntry>>()
    for (entry in entries) {
        val template = entry[Template] ?: continue
        grouped.getOrPut(entry.role to template) { mutableListOf() } += entry
    }
    return grouped.map { (key, entries) -> DeclaredTemplate(role = key.first, template = key.second, entries = entries) }
}

/**
 * [spec] resolved against [table], the way the design draft's section 3 reads a `--arg template=`
 * entry: [spec] tried whole, as a reference to a role with exactly one template, and once more
 * with its last `.`-segment read as an id -- since a role and a group may share the same `.`
 * separator, a spec such as `feature.Screen` can mean either "group feature, role Screen" or
 * "role feature, id Screen" until the name table says which one exists.
 *
 * Neither reading ever guesses a group from a suffix: a spec that leaves its group out only
 * resolves when the plain role name (unqualified, [Role.name]) picks exactly one templated role,
 * mirroring how the pre-0.2 `--arg roleName=` resolved a short name.
 *
 * @throws KatachiAmbiguousTemplateException when [spec] resolves to more than one template --
 *   either because both readings hit, or because [spec] names a role by its plain or qualified
 *   name alone and that role has more than one template.
 * @throws KatachiUnknownTemplateException when [spec] resolves to no template at all.
 */
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

/** What `--arg template=` chose: templates to run together, or one template set. */
internal sealed interface TemplateTarget {
    class Templates(val chosen: List<DeclaredTemplate>) : TemplateTarget

    class Set(val declaration: TemplateSetDeclaration) : TemplateTarget
}

/**
 * [requested] resolved to what a run generates.
 *
 * Each specifier is read as a template set (its full specifier, or its name alone while exactly
 * one set has that name) and as a template at the same time. A specifier both read reach is
 * ambiguous, with both among the candidates. A set is generated alone: naming it with any other
 * specifier is refused.
 *
 * @throws KatachiAmbiguousTemplateException when a specifier reaches more than one template or set.
 * @throws KatachiUnknownTemplateException when a specifier reaches nothing.
 * @throws KatachiInvalidTemplateSpecifierException when a set is named together with another
 *   specifier.
 */
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

/**
 * `raw` (an `--arg template=` value, still undecoded) split the same way
 * [me.tbsten.katachi.processor.internal.StringMapDecoder] splits a `List<String>` field: an empty
 * string is an empty list, everything else on `,`.
 *
 * Needed here because [me.tbsten.katachi.template.GenerateCodeFromTemplate.undeclaredArgNames]
 * reads [me.tbsten.katachi.processor.ArchitectureProcessContext.rawArgs] before anything has
 * decoded `Args`, where the comma has not been split yet.
 */
internal fun splitTemplateArg(raw: String): List<String> = if (raw.isEmpty()) emptyList() else raw.split(',')

/**
 * Refuses [specifiers] as `--arg template=` gives them: empty (`--arg template=`), an empty
 * element (`a,,b`), or the same specifier twice (`a,a`). Textual duplicates only -- two different
 * specifiers that resolve to the same template are caught once resolution has run, by
 * [requireNoDuplicateTemplates].
 */
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

/**
 * Refuses [chosen] when two of [requested]'s specifiers -- however differently spelled -- resolved
 * to the same template: `data.Repository.repository` and `Repository.repository` both reaching
 * the one template are the same mistake `a,a` is, just spelled two ways.
 */
internal fun requireNoDuplicateTemplates(requested: List<String>, chosen: List<DeclaredTemplate>) {
    if (chosen.map { it.specifier }.toSet().size == chosen.size) return
    throw KatachiInvalidTemplateSpecifierException(
        problem = KatachiInvalidTemplateSpecifierException.Problem.Duplicate,
        specifiers = requested,
    )
}
