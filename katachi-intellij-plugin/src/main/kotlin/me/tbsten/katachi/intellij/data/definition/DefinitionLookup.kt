package me.tbsten.katachi.intellij.data.definition

import me.tbsten.katachi.intellij.model.ConstraintModel
import me.tbsten.katachi.intellij.model.DefinitionSnapshot
import me.tbsten.katachi.intellij.model.FileDeclarationsModel
import me.tbsten.katachi.intellij.model.FileMatch
import me.tbsten.katachi.intellij.model.FileRoles
import me.tbsten.katachi.intellij.model.Freshness
import me.tbsten.katachi.intellij.model.RoleFileDeclarations
import me.tbsten.katachi.intellij.model.RoleModel
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

/** Where a path is to one definition: the snapshot whose project root holds it, and the path relative to that root. */
internal data class LocatedPath(val snapshot: DefinitionSnapshot, val projectRoot: Path, val relativePath: String)

/**
 * Answers the questions of `DefinitionModelService` for one list of definitions (design 4.5): which definition
 * a path is under (the deepest project root, when roots nest), which roles hold it, and the explanation of its
 * declarations. Pure: paths only, no read lock, no index. Built once per loaded list; the matchers, the answers
 * per path and the map of explained files are made on first use and kept.
 *
 * A definition whose walk could not run has no project root; its module's linked Gradle root stands in.
 *
 * ```kotlin
 * val lookup = DefinitionLookup(ready.definitions)
 * val roles = lookup.forPath(file, ready.freshness)?.matches
 * ```
 */
internal class DefinitionLookup(val definitions: List<DefinitionSnapshot>) {
    private class Root(val snapshot: DefinitionSnapshot, val path: Path, val order: Int)

    /** Deepest root first, so that nested roots answer from the inner definition. */
    private val roots: List<Root> = definitions.mapIndexedNotNull { order, snapshot ->
        val definition = snapshot.definition ?: return@mapIndexedNotNull null
        Root(snapshot, (definition.projectRoot ?: snapshot.module.linkedRootPath).normalize(), order)
    }.sortedWith(compareByDescending<Root> { it.path.nameCount }.thenBy { it.order })

    private val matchers = ConcurrentHashMap<Int, DefinitionMatcher>()
    private val matches = ConcurrentHashMap<Pair<Int, String>, FileMatch>()
    private val explained = ConcurrentHashMap<Int, Map<String, List<Explained>>>()

    private class Explained(val role: RoleModel, val constraint: ConstraintModel, val declarations: FileDeclarationsModel)

    /** The definition [path] (absolute) is under, or `null` when no project root holds it. */
    fun locate(path: Path): LocatedPath? {
        val normalized = path.normalize()
        val root = roots.firstOrNull { normalized.startsWith(it.path) && normalized != it.path } ?: return null
        return LocatedPath(root.snapshot, root.path, root.path.relativize(normalized).joinToString("/"))
    }

    /** Which roles hold [path], with [freshness] as the answer's. `null` when no definition is above it. */
    fun forPath(path: Path, freshness: Freshness): FileRoles? {
        val located = locate(path) ?: return null
        val order = orderOf(located)
        val match = matches.computeIfAbsent(order to located.relativePath) { (_, relative) -> matcherOf(order, located).matchFile(relative) }
        return match.within(located.snapshot.module, located.projectRoot, located.relativePath, freshness)
    }

    /** The explanation of [path]'s declarations, one per role and `ktFileContent { }` constraint that wrote one for it. */
    fun fileDeclarationsOf(path: Path, freshness: Freshness): List<RoleFileDeclarations> {
        val located = locate(path) ?: return emptyList()
        val byFile = explained.computeIfAbsent(orderOf(located)) { explainedFilesOf(located.snapshot) }
        return byFile[located.relativePath].orEmpty().map { RoleFileDeclarations(it.role, it.constraint, it.declarations, freshness) }
    }

    private fun orderOf(located: LocatedPath): Int = roots.first { it.snapshot === located.snapshot }.order

    private fun matcherOf(order: Int, located: LocatedPath): DefinitionMatcher =
        matchers.computeIfAbsent(order) { DefinitionMatcher(located.snapshot.definition ?: error("located only among definitions")) }

    /** "Relative path → explanations", made once from `roles[].constraints[].fileDeclarations[].file` (design 4.5). */
    private fun explainedFilesOf(snapshot: DefinitionSnapshot): Map<String, List<Explained>> {
        val result = HashMap<String, MutableList<Explained>>()
        for (role in snapshot.definition?.roles.orEmpty()) {
            for (constraint in role.constraints) {
                for (file in constraint.fileDeclarations.orEmpty()) {
                    result.getOrPut(file.file) { mutableListOf() } += Explained(role, constraint, file)
                }
            }
        }
        return result
    }
}
