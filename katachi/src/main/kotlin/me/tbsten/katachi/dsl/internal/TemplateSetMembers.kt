package me.tbsten.katachi.dsl.internal

import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.KatachiInvalidTemplateSetMemberException
import me.tbsten.katachi.dsl.KatachiInvalidTemplateSetMemberException.Problem
import me.tbsten.katachi.dsl.LayoutEntry

internal fun requireTemplateSetMembersResolve(sets: List<TemplateSetDeclaration>, entries: List<LayoutEntry>) {
    if (sets.isEmpty()) return
    val candidates = LinkedHashMap<Pair<Any, LayoutTemplate>, SpecifierCandidate>()
    for (entry in entries) {
        val template = entry[Template] ?: continue
        candidates.getOrPut(entry.role to template) {
            SpecifierCandidate(entry.role.qualifiedName, entry.role.name, template.id)
        }
    }
    val table = candidates.values.toList()
    for (set in sets) {
        // Per set: another set may list the same template. Maps a template to its first member's line.
        val seen = mutableMapOf<Int, DeclarationSite>()
        for (member in set.members) {
            fun fail(problem: Problem, found: List<String>, first: DeclarationSite? = null): Nothing = throw KatachiInvalidTemplateSetMemberException(
                templateSet = set.specifier,
                member = member.specifier,
                problem = problem,
                candidates = found,
                declaredAt = member.declaredAt,
                firstDeclaredAt = first,
            )
            when (val resolved = resolveSpecifier(member.specifier, table)) {
                is SpecifierResolution.Found -> {
                    val first = seen.putIfAbsent(resolved.index, member.declaredAt)
                    if (first != null) fail(Problem.Duplicate, listOf(table[resolved.index].specifier), first)
                }
                is SpecifierResolution.Ambiguous -> fail(Problem.Ambiguous, resolved.specifiers)
                SpecifierResolution.Unknown -> fail(Problem.Unknown, table.map { it.specifier }.distinct().sorted())
            }
        }
    }
}
