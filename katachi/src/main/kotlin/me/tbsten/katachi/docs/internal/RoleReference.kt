package me.tbsten.katachi.docs.internal

import me.tbsten.katachi.docs.KatachiBrokenDocumentLinkException
import me.tbsten.katachi.docs.KatachiDocumentPathCaseCollisionException
import me.tbsten.katachi.docs.KatachiDocumentPathCollisionException
import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.Description
import me.tbsten.katachi.dsl.Group
import me.tbsten.katachi.dsl.Role
import me.tbsten.katachi.dsl.Title
import me.tbsten.katachi.dsl.internal.ModuleIndex
import me.tbsten.katachi.dsl.internal.evaluateLayout
import me.tbsten.katachi.processor.ArchitectureProcessContext

internal fun roleReferenceDocuments(
    context: ArchitectureProcessContext<*>,
): Map<String, String> {
    val architecture = context.architecture
    // One evaluation, not `context.declaredEntries` plus a second one for the constraints. The
    // entries of a `layout { }` and the constraints written inside it come out of the same run of
    // the deferred blocks, and reading them off two runs is one more chance for the two to stop
    // agreeing about the very blocks they both describe. The entries are the same list
    // `declaredEntries` answers with -- both go through an unresolved module index, so nothing
    // here reads the project.
    val evaluation = architecture.evaluateLayout(ModuleIndex.unresolved(architecture.moduleResolver))
    val placements = placementsOf(evaluation.entries)
    // Asked per page rather than up front: a constraint's section is only worked out for a role
    // that gets a page, and working it out may list the project's files (see
    // `constraintSectionsOf`).
    val constraintsByRole = evaluation.fileConstraints.groupBy { it.role }
    val constraintSections: (Role) -> ConstraintDocumentation = { role ->
        constraintSectionsOf(context, role, constraintsByRole[role].orEmpty())
    }

    // Decided once and handed to both the page and every breadcrumb that points back at it. A
    // reader who saw `# myapp documentation` and then `[Architecture](../README.md)` two clicks
    // later has to work out that the two are the same page.
    val rootName = architecture.metadata[Title].orBlank() ?: ROOT_TITLE

    val documents = Documents()
    documents.put(
        path = README,
        owner = Owner("The documentation root", DeclarationSite.Unknown),
        content = rootPage(
            title = rootName,
            description = architecture.metadata[Description].orBlank(),
            metadata = architecture.metadata,
            roles = architecture.roles.filter { it.isDocumented },
            groups = architecture.groups.filter { it.isDocumented },
            placements = placements,
        ),
    )
    for (role in architecture.roles.filter { it.isDocumented }) {
        // A role written at the root sits beside the root `README.md`, so its one step back is
        // `README.md` with no `../` in front of it. `breadcrumbOf` counts that from a depth of 0.
        documents.putRole(
            role,
            directory = "",
            placements,
            constraintSections,
            breadcrumbOf(rootName, emptyList(), depth = 0),
        )
    }
    for (group in architecture.groups) {
        documents.putGroup(group, rootName, ancestors = emptyList(), placements, constraintSections)
    }
    return documents.pages().also(::checkDocumentLinks)
}

private fun String?.orBlank(): String? = this?.takeIf { it.isNotBlank() }

private fun Documents.putGroup(
    group: Group,
    rootName: String,
    ancestors: List<Group>,
    placements: Map<Role, List<Placement>>,
    constraintSections: (Role) -> ConstraintDocumentation,
) {
    // A group that opted out takes its subtree with it. Its directory is never created, so a page
    // written inside it would sit somewhere nothing links to.
    if (!group.isDocumented) return
    val directory = "${group.directoryPath()}/"
    put(
        path = "$directory$README",
        owner = Owner("Group \"${group.qualifiedName}\"", group.declaredAt),
        content = containerPage(
            title = group.displayName,
            metadata = group.metadata,
            roles = group.roles.filter { it.isDocumented },
            groups = group.groups.filter { it.isDocumented },
            placements = placements,
            ancestors = breadcrumbOf(rootName, ancestors, depth = group.path.size),
        ),
    )
    for (role in group.roles.filter { it.isDocumented }) {
        // The group itself is the last step, because a role page is not the `README.md` of the
        // directory it is in: the way back for its reader ends at the page that lists it.
        putRole(
            role,
            directory,
            placements,
            constraintSections,
            breadcrumbOf(rootName, ancestors + group, depth = group.path.size),
        )
    }
    for (child in group.groups) {
        putGroup(child, rootName, ancestors + group, placements, constraintSections)
    }
}

private fun breadcrumbOf(rootName: String, ancestors: List<Group>, depth: Int): List<Crumb> =
    listOf(Crumb(text = rootName, path = readmeUp(depth))) +
        ancestors.mapIndexed { index, ancestor ->
            Crumb(text = ancestor.displayName, path = readmeUp(depth - (index + 1)))
        }

private fun readmeUp(steps: Int): String = "../".repeat(steps) + README

private fun Documents.putRole(
    role: Role,
    directory: String,
    placements: Map<Role, List<Placement>>,
    constraintSections: (Role) -> ConstraintDocumentation,
    ancestors: List<Crumb>,
) = put(
    path = "$directory${role.name}$PAGE_EXTENSION",
    owner = Owner("Role \"${role.qualifiedName}\"", role.declaredAt),
    content = rolePage(
        role = role,
        placements = placements[role].orEmpty(),
        constraints = constraintSections(role),
        ancestors = ancestors,
    ),
)

private class Owner(val label: String, val declaredAt: DeclarationSite) {
    fun describe(): String = label + writtenAt(declaredAt)
}

private class Documents {
    private val contents = LinkedHashMap<String, String>()
    private val owners = HashMap<String, Owner>()

    fun put(path: String, owner: Owner, content: String) {
        val first = owners[path]
        if (first != null) {
            throw KatachiDocumentPathCollisionException(
                path = path,
                declaration = owner.label,
                declaredAt = owner.declaredAt,
                firstDeclaration = first.label,
                firstDeclaredAt = first.declaredAt,
            )
        }
        owners[path] = owner
        contents[path] = content
    }

    fun pages(): Map<String, String> {
        val collisions = contents.keys
            .groupBy { it.lowercase() }
            .values
            .filter { it.size > 1 }
        if (collisions.isNotEmpty()) {
            throw KatachiDocumentPathCaseCollisionException(
                collisions = collisions,
                owners = collisions.flatten().associateWith { owners[it]?.describe().orEmpty() },
            )
        }
        return contents
    }
}
