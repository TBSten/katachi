package me.tbsten.katachi.docs

import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.Group
import me.tbsten.katachi.dsl.ModuleIndex
import me.tbsten.katachi.dsl.Role
import me.tbsten.katachi.dsl.evaluateLayout
import me.tbsten.katachi.processor.ArchitectureProcessContext

/**
 * The whole role reference, as a path relative to the output root mapped to the text of the page.
 *
 * It writes nothing. Deciding *where* files go is the processor's business and touching the disk
 * is the shell's; what is here is the part worth testing, and it is testable precisely because a
 * spec can compare two strings instead of setting up a directory. The same separation
 * `KatachiEntryPointSource` makes in the Gradle plugin, for the same reason.
 *
 * The output is one `README.md` per container plus one page per role:
 *
 * ```text
 * README.md            the groups declared at the root, and the roles declared beside them
 * domain/README.md     the roles of `domain`, and the groups inside it
 * domain/UseCase.md    one role
 * ```
 *
 * Paths come from identifiers, never from `title`, so nothing in the tree needs escaping and a
 * link between two pages is the identifier spelled out.
 *
 * ## Example 1: build the pages of a definition without touching the disk
 * ```kt
 * val arch = architecture {
 *     "domain".group {
 *         title = "ドメイン"
 *         "UseCase" { title = "ユースケース" }
 *     }
 * }
 *
 * val pages = arch.process { context -> roleReferenceDocuments(context) }
 * pages.keys shouldContainExactly listOf("README.md", "domain/README.md", "domain/UseCase.md")
 * ```
 *
 * @throws KatachiDocumentPathCollisionException when two declarations name the same page.
 */
// TODO(v0.2 ステップ6): --arg mode=check
//  Comparing instead of writing needs nothing more of this function -- the caller reads the
//  files it would have written and diffs them against this map. The flag is the shell's.
internal fun roleReferenceDocuments(context: ArchitectureProcessContext<*>): Map<String, String> {
    val architecture = context.architecture
    // One evaluation, not `context.declaredEntries` plus a second one for the constraints. The
    // entries of a `layout { }` and the constraints written inside it come out of the same run of
    // the deferred blocks, and reading them off two runs is one more chance for the two to stop
    // agreeing about the very blocks they both describe. The entries are the same list
    // `declaredEntries` answers with -- both go through an unresolved module index, so nothing
    // here reads the project.
    val evaluation = architecture.evaluateLayout(ModuleIndex.unresolved(architecture.moduleResolver))
    val placements = placementsOf(evaluation.entries)
    val constraintNames = evaluation.constraints
        .mapNotNull { constraint -> constraint.name?.let { constraint.role to it } }
        .groupBy({ it.first }, { it.second })

    val documents = Documents()
    documents.put(
        path = README,
        owner = Owner("The documentation root", DeclarationSite.Unknown),
        content = containerPage(
            title = ROOT_TITLE,
            roles = architecture.roles.filter { it.isDocumented },
            groups = architecture.groups.filter { it.isDocumented },
            // TODO(v0.2): decide whether the root shows a placement tree of the roles declared
            //  beside the groups. What is settled covers a group's README only, and a tree here
            //  would be the one place a reader could not tell which group it is about.
            placements = emptyMap(),
        ),
    )
    for (role in architecture.roles.filter { it.isDocumented }) {
        documents.putRole(role, directory = "", placements, constraintNames)
    }
    for (group in architecture.groups) {
        documents.putGroup(group, placements, constraintNames)
    }
    return documents.pages()
}

/** A group's own page, its roles' pages, and — recursively — everything below it. */
private fun Documents.putGroup(
    group: Group,
    placements: Map<Role, List<Placement>>,
    constraintNames: Map<Role, List<String>>,
) {
    // A group that opted out takes its subtree with it. Its directory is never created, so a page
    // written inside it would sit somewhere nothing links to.
    if (!group.isDocumented) return
    val directory = "${group.qualifiedName}/"
    put(
        path = "$directory$README",
        owner = Owner("Group \"${group.qualifiedName}\"", group.declaredAt),
        content = containerPage(
            title = group.displayName,
            roles = group.roles.filter { it.isDocumented },
            groups = group.groups.filter { it.isDocumented },
            placements = placements,
        ),
    )
    for (role in group.roles.filter { it.isDocumented }) {
        putRole(role, directory, placements, constraintNames)
    }
    for (child in group.groups) {
        putGroup(child, placements, constraintNames)
    }
}

/** One role's page, below [directory] — empty for a role declared at the root. */
private fun Documents.putRole(
    role: Role,
    directory: String,
    placements: Map<Role, List<Placement>>,
    constraintNames: Map<Role, List<String>>,
) = put(
    path = "$directory${role.name}$PAGE_EXTENSION",
    owner = Owner("Role \"${role.qualifiedName}\"", role.declaredAt),
    content = rolePage(
        role = role,
        placements = placements[role].orEmpty(),
        constraintNames = constraintNames[role].orEmpty(),
    ),
)

/** What produced a page, for the collision message to name. */
private class Owner(val label: String, val declaredAt: DeclarationSite)

/**
 * The pages built so far, refusing to let one overwrite another.
 *
 * A plain `MutableMap` would answer a collision by keeping the later page, which is the one
 * outcome nobody could notice from the output.
 */
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

    fun pages(): Map<String, String> = contents
}
