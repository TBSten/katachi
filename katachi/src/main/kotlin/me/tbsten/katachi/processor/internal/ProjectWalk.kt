package me.tbsten.katachi.processor.internal

import me.tbsten.katachi.check.Violation
import me.tbsten.katachi.check.internal.FileOverlap
import me.tbsten.katachi.check.internal.scanProject
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.Group
import me.tbsten.katachi.dsl.LayoutEntry
import me.tbsten.katachi.dsl.Role
import me.tbsten.katachi.dsl.files.FsPath
import me.tbsten.katachi.dsl.files.KatachiFileSystem
import me.tbsten.katachi.dsl.internal.DeclaredFileConstraint
import me.tbsten.katachi.dsl.internal.flattenLayout
import me.tbsten.katachi.processor.KatachiUnknownRoleException

/**
 * One run's worth of reading: the declarations, the single walk of the project, and the
 * bookkeeping a run of checks shares.
 *
 * It is a class of its own rather than fields on [RealArchitectureProcessContext] because
 * `withArgs` hands the same run to another processor. If the walk, the set of evaluated
 * constraints and the constraint scratch lived on the context, each `withArgs` would fork
 * them -- and a forked "evaluated" set is the quietest way this library can break: the
 * constraints one check answered for would still look unevaluated to the run that reports
 * them, or the other way round. One object, passed along by reference, makes that impossible
 * to get wrong.
 */
internal class ProjectWalk(
    val architecture: Architecture,
    val fileSystem: KatachiFileSystem,
) {
    val groups: List<Group> get() = architecture.allGroups

    val roles: List<Role> get() = architecture.allRoles

    val declaredEntries: List<LayoutEntry> by lazy { architecture.flattenLayout() }

    // `by lazy` rather than a field is the whole of the "no IO until `filesOf`" promise:
    // building this resolves the project root, lists the modules and walks the tree.
    private val scan by lazy { architecture.scanProject(fileSystem) }

    // Built from the declarations alone, so asking costs no IO -- which is what lets `filesOf`
    // reject a foreign role without first walking the project. A plain `HashSet` on purpose:
    // it has to decide "same role" the way the walk's own `Map<Role, ...>` decides it.
    private val ownRoles: Set<Role> by lazy { architecture.allRoles.toHashSet() }

    fun filesOf(role: Role): List<String> {
        if (role !in ownRoles) throw KatachiUnknownRoleException(role)
        return scan.filesByRole[role].orEmpty()
    }

    val layoutViolations: List<Violation> get() = scan.violations

    val layoutFileOverlaps: List<FileOverlap> get() = scan.fileOverlaps

    val declaredFileConstraints: List<DeclaredFileConstraint> get() = scan.fileConstraints

    /** Where the walk started -- what a report resolves each relative path against. */
    val projectRoot: FsPath get() = scan.projectRoot

    /** Where the walk started, absolute -- what a constraint backend opens files from. */
    val projectRootPath: String get() = projectRoot.value

    // `DeclaredFileConstraint` declares no `equals`, exactly as `Role` does not, so this is a set
    // of identities. One walk is built per run, so what collects here is precisely "evaluated
    // during this one run" -- which is what lets `assert(FileConstraintCheck())` be told apart from
    // `assert()` without anyone inspecting the types of the checks passed in.
    private val evaluated: MutableSet<DeclaredFileConstraint> = HashSet()

    fun hasEvaluated(constraint: DeclaredFileConstraint): Boolean = constraint in evaluated

    fun markEvaluated(constraint: DeclaredFileConstraint) {
        evaluated += constraint
    }

    /** The constraints nothing answered for, which the run has to report rather than drop. */
    val unevaluatedFileConstraints: List<DeclaredFileConstraint>
        get() = declaredFileConstraints.filterNot { it in evaluated }

    // Same lifetime as `evaluated` and kept in the same place, so that
    // `FileConstraintSubject.memo`'s "every constraint of one run" means one thing in the
    // documentation and in the code.
    private val constraintScratch: MutableMap<Any, Any> = mutableMapOf()

    fun scratch(): MutableMap<Any, Any> = constraintScratch

    /**
     * The files [constraint] covers, in walk order: the role's own files narrowed by the
     * constraint's coverage.
     */
    fun filesUnder(constraint: DeclaredFileConstraint): List<String> =
        scan.filesByRole[constraint.role].orEmpty().filter { constraint.coverage.covers(it) }

    // Deliberately says nothing about the files: printing a context must not be what starts a
    // walk of the project.
    override fun toString(): String =
        "ProjectWalk(groups=${groups.size}, roles=${roles.size}, $fileSystem)"
}
