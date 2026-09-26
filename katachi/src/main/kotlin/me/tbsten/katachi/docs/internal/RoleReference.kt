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
 * ## What names the root
 *
 * The heading and the paragraph of the root page are `architecture { }`'s own `title` and
 * `description`, the same words a group writes about itself. They used to also be settable per
 * run, through `--arg` and the Gradle plugin's `docs { }` block, with the run winning; there is
 * now one place to write them, because what a repository is called is a fact about the
 * repository rather than about one run of one tool. Blank counts as unwritten, so a computed
 * value that came out empty asks for the fallback rather than for an empty heading.
 *
 * @throws KatachiDocumentPathCollisionException when two declarations name the same page.
 * @throws KatachiDocumentPathCaseCollisionException when two pages are one file on a case
 * insensitive filesystem.
 * @throws KatachiBrokenDocumentLinkException when a generated link resolves to no generated page.
 */
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
    val constraintNames = evaluation.fileConstraints
        .mapNotNull { constraint -> constraint.name?.let { constraint.role to it } }
        .groupBy({ it.first }, { it.second })

    // Decided once and handed to both the page and every breadcrumb that points back at it. A
    // reader who saw `# myapp ドキュメント` and then `[アーキテクチャ](../README.md)` two clicks
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
        documents.putRole(role, directory = "", placements, constraintNames, breadcrumbOf(rootName, emptyList(), depth = 0))
    }
    for (group in architecture.groups) {
        documents.putGroup(group, rootName, ancestors = emptyList(), placements, constraintNames)
    }
    return documents.pages().also(::checkDocumentLinks)
}

/**
 * This value when something was actually written, and `null` when it was blank.
 *
 * Blank is unwritten: a definition that computes its own title -- from a version, from a property
 * -- can come out empty without having to know what the fallback would have been, and a page with
 * no heading is not what that asks for.
 */
private fun String?.orBlank(): String? = this?.takeIf { it.isNotBlank() }

/** A group's own page, its roles' pages, and — recursively — everything below it. */
private fun Documents.putGroup(
    group: Group,
    rootName: String,
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
            constraintNames,
            breadcrumbOf(rootName, ancestors + group, depth = group.path.size),
        )
    }
    for (child in group.groups) {
        putGroup(child, rootName, ancestors + group, placements, constraintNames)
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
private fun breadcrumbOf(rootName: String, ancestors: List<Group>, depth: Int): List<Crumb> =
    listOf(Crumb(text = rootName, path = readmeUp(depth))) +
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
    ancestors: List<Crumb>,
) = put(
    path = "$directory${role.name}$PAGE_EXTENSION",
    owner = Owner("Role \"${role.qualifiedName}\"", role.declaredAt),
    content = rolePage(
        role = role,
        placements = placements[role].orEmpty(),
        constraintNames = constraintNames[role].orEmpty(),
        ancestors = ancestors,
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
