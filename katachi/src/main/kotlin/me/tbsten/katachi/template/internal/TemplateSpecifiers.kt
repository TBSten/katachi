package me.tbsten.katachi.template.internal

import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.LayoutEntry
import me.tbsten.katachi.dsl.Role
import me.tbsten.katachi.dsl.internal.LayoutTemplate
import me.tbsten.katachi.dsl.internal.Template
import me.tbsten.katachi.template.KatachiAmbiguousTemplateException
import me.tbsten.katachi.template.KatachiInvalidTemplateSpecifierException
import me.tbsten.katachi.template.KatachiUnknownTemplateException

/**
 * The `--arg` name that says which templates to run or describe. Spelled once, because
 * [me.tbsten.katachi.template.GenerateCodeFromTemplate.undeclaredArgNames] has to read it out of
 * the raw arguments before anything has decoded the processor's `Args`, and a capture of that
 * name is refused.
 */
internal const val TEMPLATE_ARG: String = "template"

/** The `--arg` name of `GenerateCodeFromTemplate.Args.onExisting`, which a capture may not take. */
internal const val ON_EXISTING_ARG: String = "onExisting"

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
internal fun resolveTemplate(spec: String, table: List<DeclaredTemplate>): DeclaredTemplate {
    // No fast path on an exact `specifier` match: reading 1 below already finds it whenever
    // [spec] names a role by its full qualifiedName (with or without an id), and skipping
    // straight to it would hide a genuine collision with reading 2 -- see this function's KDoc.
    val wholeRoleMatches = rolesMatching(spec, table)
    val templatesOfWholeRole = wholeRoleMatches.singleOrNull()?.let { role -> table.filter { it.role === role } }.orEmpty()
    val readAsRole = templatesOfWholeRole.singleOrNull()

    val lastDot = spec.lastIndexOf('.')
    val readAsRoleAndId = if (lastDot in 1 until spec.lastIndex + 1) {
        val rolePart = spec.substring(0, lastDot)
        val idPart = spec.substring(lastDot + 1)
        rolesMatching(rolePart, table).singleOrNull()?.let { role -> table.firstOrNull { it.role === role && it.id == idPart } }
    } else {
        null
    }

    val hits = listOfNotNull(readAsRole, readAsRoleAndId).distinctBy { it.specifier }
    return when {
        hits.size == 1 -> hits.single()
        hits.size > 1 -> throw KatachiAmbiguousTemplateException(
            specifier = spec,
            candidates = hits.map { it.specifier }.sorted(),
        )
        // `spec` named a role -- by its plain name or its qualified one -- with more than one
        // template and no id to choose among them, rather than two different readings.
        templatesOfWholeRole.size > 1 -> throw KatachiAmbiguousTemplateException(
            specifier = spec,
            candidates = templatesOfWholeRole.map { it.specifier }.sorted(),
        )
        // `spec` left its group out (a plain qualifiedName match above would already have
        // returned exactly one role) and that short name reaches more than one templated role --
        // group.Role / group.Role.id, not group left out at all, is what tells them apart.
        wholeRoleMatches.size > 1 -> throw KatachiAmbiguousTemplateException(
            specifier = spec,
            candidates = wholeRoleMatches
                .flatMap { role -> table.filter { it.role === role } }
                .map { it.specifier }
                .sorted(),
        )
        else -> throw KatachiUnknownTemplateException(
            specifier = spec,
            declaredTemplates = table.map { it.specifier }.distinct().sorted(),
        )
    }
}

/**
 * Every role [reference] could name among [table]'s roles: an exact [Role.qualifiedName] match
 * first (at most one, since a qualified name is unique), and -- only when none matches that way
 * -- every templated role sharing that plain [Role.name] second, however many there are. Never a
 * suffix or partial match: a group is either written in full or left out entirely.
 *
 * More than one match here is what a spec leaving its group out, reaching more than one role by
 * the same short name, looks like -- [resolveTemplate] is what turns that into
 * [KatachiAmbiguousTemplateException] rather than [KatachiUnknownTemplateException]: a role this
 * ambiguous name could mean is not the same as no role meaning it at all.
 */
private fun rolesMatching(reference: String, table: List<DeclaredTemplate>): List<Role> {
    table.firstOrNull { it.role.qualifiedName == reference }?.let { return listOf(it.role) }
    return table.map { it.role }.distinct().filter { it.name == reference }
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
