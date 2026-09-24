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
 * @throws KatachiDocumentPathCaseCollisionException when two pages are one file on a case
 * insensitive filesystem.
 * @throws KatachiBrokenDocumentLinkException when a generated link resolves to no generated page.
 */
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
            placements = placements,
            placementHeading = ROOT_PLACEMENT_HEADING,
            // The root is what every breadcrumb ends up pointing at, so it has nothing above it.
            ancestors = emptyList(),
        ),
    )
    for (role in architecture.roles.filter { it.isDocumented }) {
        documents.putRole(role, directory = "", placements, constraintNames)
    }
    for (group in architecture.groups) {
        documents.putGroup(group, ancestors = emptyList(), placements, constraintNames)
    }
    return documents.pages().also(::checkDocumentLinks)
}

/** A group's own page, its roles' pages, and — recursively — everything below it. */
private fun Documents.putGroup(
    group: Group,
    ancestors: List<Group>,
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
            placementHeading = GROUP_PLACEMENT_HEADING,
            ancestors = breadcrumbOf(ancestors, depth = group.path.size),
        ),
    )
    for (role in group.roles.filter { it.isDocumented }) {
        putRole(role, directory, placements, constraintNames)
    }
    for (child in group.groups) {
        putGroup(child, ancestors + group, placements, constraintNames)
    }
}

/**
 * The way back out of a group whose `README.md` sits [depth] directories below the output root.
 *
 * The number of `../` steps is a property of the two pages rather than of the code that writes
 * the link, so it is counted: an ancestor [depth] levels up from the page, one fewer for each
 * step down. The root comes first and is always there, which is what gives a group one level
 * down a breadcrumb too — one step, to the page that lists it.
 */
private fun breadcrumbOf(ancestors: List<Group>, depth: Int): List<Crumb> =
    listOf(Crumb(text = ROOT_TITLE, path = readmeUp(depth))) +
        ancestors.mapIndexed { index, ancestor ->
            Crumb(text = ancestor.displayName, path = readmeUp(depth - (index + 1)))
        }

/** The `README.md` [steps] directories above the page the link is written on. */
private fun readmeUp(steps: Int): String = "../".repeat(steps) + README

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

/** What produced a page, for the collision messages to name. */
private class Owner(val label: String, val declaredAt: DeclarationSite) {
    /** `Role "domain/UseCase" at ProjectArchitecture.kt:42`, as a message writes it out. */
    fun describe(): String = label + writtenAt(declaredAt)
}

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

    /**
     * The pages, once no two of them are the same file.
     *
     * Case is asked about here rather than in [put] because, unlike an exact collision, it is a
     * question about the whole set: a page is not wrong on its own, only next to another one.
     * Reporting them together follows from that, and spares a reader one run per clash.
     */
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
