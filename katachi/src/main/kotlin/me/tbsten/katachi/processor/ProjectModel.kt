package me.tbsten.katachi.processor

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.Group
import me.tbsten.katachi.dsl.LayoutEntry
import me.tbsten.katachi.dsl.Role
import me.tbsten.katachi.dsl.flattenLayout
import me.tbsten.katachi.fs.KatachiFileSystem
import me.tbsten.katachi.scan.Violation
import me.tbsten.katachi.scan.scanProject

/**
 * What an [ArchitectureProcessor] is handed: the declarations, and — on request — the files
 * that turned out to match them.
 *
 * It is read only. Where a processor writes its output is the processor's own business, so
 * there is no sink here to write through; a processor that produces files takes the write
 * operation as a constructor parameter, which is also what makes it testable and what makes a
 * "compare instead of write" mode a different lambda rather than a mode flag.
 *
 * **Nothing here touches the file system until [filesOf] is called**, the search for the
 * project root included. A processor that only reads declarations therefore runs with no IO
 * at all, which is the point: it can run inside an `architecture { }` unit test, or against a
 * definition whose project is not checked out.
 *
 * That is also why the two halves are named apart. [declaredEntries] is what the definition
 * *says*, read without looking at anything; [filesOf] is what the project *has*. They answer
 * different questions and a wildcard module key is where the difference shows — see
 * [declaredEntries].
 *
 * ## Example 1: read the declarations without touching the project
 * ```kt
 * val roleNames = projectArchitecture.process { model -> model.roles.map { it.qualifiedName } }
 * roleNames shouldContain "domain/UseCase"
 * ```
 *
 * ## Example 2: ask for the files a role actually owns
 * ```kt
 * val useCase = projectArchitecture.allRoles.single { it.name == "UseCase" }
 * val files = projectArchitecture.process { model -> model.filesOf(useCase) }
 * files shouldContain "core/domain/useCase/GetUserUseCase.kt"
 * ```
 */
@ExperimentalKatachiApi
public class ProjectModel internal constructor(
    private val architecture: Architecture,
    private val fileSystem: KatachiFileSystem,
) {
    /**
     * Every group, parents before their children, in declaration order.
     *
     * Flat rather than only the top level ones, so that a processor asking "which groups
     * carry this metadata" does not have to write the recursion itself. The tree is still
     * there: [Group.groups] holds the children, and [Group.path] says where a group sits.
     *
     * ## Example 1: list every group, nested ones included
     * ```kt
     * val arch = architecture {
     *     "domain".group { "model".group { "Entity" { } } }
     * }
     * arch.process { it.groups.map { group -> group.qualifiedName } } shouldContainExactly
     *     listOf("domain", "domain/model")
     * ```
     */
    public val groups: List<Group> get() = architecture.allGroups

    /**
     * Every role, those declared at the root of `architecture { }` included, in declaration
     * order.
     *
     * A role that sits in no group carries an empty [Role.groupPath], and its
     * [Role.qualifiedName] is its own name. A processor that turns the path into a directory
     * therefore writes such a role at the top of its output rather than below a group.
     *
     * ## Example 1: pick the roles a processor cares about
     * ```kt
     * val arch = architecture {
     *     "domain".group { "UseCase" { } }
     *     "data".group { "Repository" { } }
     * }
     * arch.process { model -> model.roles.map { it.qualifiedName } } shouldContainExactly
     *     listOf("domain/UseCase", "data/Repository")
     * ```
     *
     * ## Example 2: a role declared at the root is one of them
     * ```kt
     * val arch = architecture {
     *     "Readme" { }
     *     "domain".group { "UseCase" { } }
     * }
     * arch.process { model -> model.roles.map { it.qualifiedName } } shouldContainExactly
     *     listOf("Readme", "domain/UseCase")
     * ```
     */
    public val roles: List<Role> get() = architecture.allRoles

    /**
     * Every `layout { }` block of every role, flattened into one list of path patterns.
     *
     * The declared shape of the project, not its actual contents: a pattern is here whether
     * or not a file matches it, which is what makes it readable without IO. `declared` is in
     * the name because that limit is the whole of what this is, and because the alternative
     * reading — "the entries of this project" — is the one that would be wrong.
     *
     * A layout key naming modules with a wildcard (`":feature:*"`) is where the two readings
     * come apart. It stands for the modules that exist, and which modules exist is a question
     * only the file system can answer — so here, where nothing is read, such a key stays
     * **one pattern**: it contributes one module's worth of entries whose paths still hold
     * the wildcard, never as many as the project happens to have feature modules. `wildcards`
     * reads as `"<name>"` inside such a block — a placeholder rather than a glob, so that a
     * name a definition builds out of it (`"${wildcards[0].pascalCase}Screen"`) comes out
     * readable as `<name>Screen.kt` and cannot collide with the glob syntax around it. Nothing
     * declared through the key is silently dropped. [filesOf] — which does walk the project —
     * sees that same key once per module that exists. A processor describing what the
     * definition says, such as documentation generation, wants the pattern; one that has to
     * name real directories works from [filesOf].
     *
     * ## Example 1: read the declared paths
     * ```kt
     * val arch = architecture {
     *     "domain".group { "UseCase" { layout { "useCase" / "GetUserUseCase".ktFile() } } }
     * }
     * arch.process { model -> model.declaredEntries.map { it.path } } shouldContainExactly
     *     listOf("useCase", "useCase/GetUserUseCase.kt")
     * ```
     *
     * ## Example 2: a wildcard module key is declared here as one pattern
     * ```kt
     * val arch = architecture {
     *     "feature".group { "Module" { layout { ":feature:*".module { } } } }
     * }
     * // Four entries however many feature modules the project holds, with the wildcard still
     * // standing where a module name would be. Split into levels only so that this example
     * // can be written inside a KDoc comment at all.
     * arch.process { model -> model.declaredEntries.map { it.path.split('/') } } shouldContainExactly
     *     listOf(
     *         listOf("feature"),
     *         listOf("feature", "*"),
     *         listOf("feature", "*", "build"),
     *         listOf("feature", "*", "build.gradle.kts"),
     *     )
     * ```
     */
    public val declaredEntries: List<LayoutEntry> by lazy { architecture.flattenLayout() }

    /**
     * The one walk of the project, run at most once and only if something asks for it.
     *
     * `by lazy` rather than a field is the whole of the "no IO until [filesOf]" promise:
     * building this resolves the project root, lists the modules and walks the tree.
     */
    private val scan by lazy { architecture.scanProject(fileSystem) }

    /**
     * [architecture]'s roles as a set, for [filesOf] to recognise its own by.
     *
     * A [Role] declares no `equals`, so this is an identity set today, and membership is
     * exactly the question [filesOf] has to answer: was this object declared in the definition
     * this model was built from. A plain `HashSet` rather than an identity set on purpose — it
     * has to decide "same role" the way the walk's own `Map<Role, ...>` decides it, so that a
     * role this accepts is one that map can be asked about.
     *
     * It is built from the declarations alone, so asking costs no IO — which is what lets
     * [filesOf] reject a foreign role without first walking the project.
     */
    private val ownRoles: Set<Role> by lazy { architecture.allRoles.toHashSet() }

    /**
     * What that same walk found wrong, for [me.tbsten.katachi.check.LayoutCheck] to return.
     *
     * Internal, and staying internal: a processor does not read violations off the model, it
     * *is* a check and produces them. This door exists only so that katachi's own check can be
     * one processor among others instead of a second walk of the tree — [filesOf] and this are
     * the same traversal seen from either side, and reading them off separate walks would let
     * them disagree about the very files they are both describing.
     *
     * Reading it starts that walk, exactly as [filesOf] does.
     */
    internal val layoutViolations: List<Violation> get() = scan.violations

    /**
     * Files that exist and that [role]'s `layout { }` allows, as project relative `/`
     * separated paths, in walk order.
     *
     * **All of them, overlaps included.** Two roles may claim patterns that both match one
     * file, and the check deliberately allows that — a file is fine as long as *some* role
     * allows it. So a file matched by two roles is returned by [filesOf] for both. Answering
     * with a single "real" owner would be inventing a rule the check does not have.
     *
     * Only files the walk reached are here. A declaration nothing matches contributes no
     * path (a missing required file is `validate()`'s business, not a processor's), and a
     * file under an `ignore()` is never looked at in the first place.
     *
     * The first call resolves the project root and walks the tree; later calls, for this role
     * or any other, reuse that one walk.
     *
     * ## Example 1: collect the files of the roles carrying a metadata key
     * ```kt
     * val platformFiles = projectArchitecture.process { model ->
     *     model.roles.filter { it.name.startsWith("Gradle") }.flatMap { model.filesOf(it) }
     * }
     * platformFiles shouldContain "settings.gradle.kts"
     * ```
     *
     * @throws KatachiUnknownRoleException when [role] was declared in some other definition.
     *   An empty list would say "this role owns nothing", which is a different statement and
     *   one nothing could catch. Checked before the walk starts, so a mistake here costs no IO.
     */
    public fun filesOf(role: Role): List<String> {
        if (role !in ownRoles) throw KatachiUnknownRoleException(role)
        return scan.filesByRole[role].orEmpty()
    }

    // Deliberately says nothing about the files: printing a model must not be what starts a
    // walk of the project.
    override fun toString(): String =
        "ProjectModel(groups=${groups.size}, roles=${roles.size}, $fileSystem)"
}
