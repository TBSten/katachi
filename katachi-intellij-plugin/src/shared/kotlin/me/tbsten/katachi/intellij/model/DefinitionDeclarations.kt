package me.tbsten.katachi.intellij.model

/**
 * Every place [this] definition says where it declared something: `architecture { }`, each group, role, layout entry,
 * `.template { }` and constraint that recorded a `declaredAt`. Pure; which of them lie in one file is the service's question
 * (`DefinitionModelService.declarationsIn`).
 *
 * A wildcard module key (`":feature:*"`) turns one declaration into several entries that share a site: each is listed, so
 * the sum over them is the caller's to take.
 *
 * ```kotlin
 * val here = definition.declarations(module.id).filter { it.site.fileName == "ServiceRole.kt" }
 * ```
 */
internal fun DefinitionModel.declarations(module: ModuleId): List<DeclarationAt> = buildList {
    architecture.declaredAt?.let { add(DeclarationAt(module, it, DeclarationKind.Architecture)) }
    for (group in groups) group.declaredAt?.let { add(DeclarationAt(module, it, DeclarationKind.Group, group = group)) }
    for (role in roles) {
        role.declaredAt?.let { add(DeclarationAt(module, it, DeclarationKind.Role, role = role)) }
        for (entry in role.layout) {
            entry.declaredAt?.let { add(DeclarationAt(module, it, DeclarationKind.Entry, role = role, entry = entry)) }
            entry.template?.declaredAt?.let { add(DeclarationAt(module, it, DeclarationKind.Template, role = role, entry = entry)) }
        }
        for (constraint in role.constraints) {
            constraint.declaredAt?.let { add(DeclarationAt(module, it, DeclarationKind.Constraint, role = role, constraint = constraint)) }
        }
    }
}
