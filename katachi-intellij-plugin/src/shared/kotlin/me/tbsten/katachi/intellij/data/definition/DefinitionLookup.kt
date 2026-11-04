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

internal data class LocatedPath(val snapshot: DefinitionSnapshot, val projectRoot: Path, val relativePath: String)

// A definition whose walk could not run has no project root; its module's linked Gradle root stands in.
internal class DefinitionLookup(val definitions: List<DefinitionSnapshot>) {
    private class Root(val snapshot: DefinitionSnapshot, val path: Path, val order: Int)

    private val roots: List<Root> = definitions.mapIndexedNotNull { order, snapshot ->
        val definition = snapshot.definition ?: return@mapIndexedNotNull null
        Root(snapshot, (definition.projectRoot ?: snapshot.module.linkedRootPath).normalize(), order)
    }.sortedWith(compareByDescending<Root> { it.path.nameCount }.thenBy { it.order })

    private val matchers = ConcurrentHashMap<Int, DefinitionMatcher>()
    private val matches = ConcurrentHashMap<Pair<Int, String>, FileMatch>()
    private val explained = ConcurrentHashMap<Int, Map<String, List<Explained>>>()

    private class Explained(val role: RoleModel, val constraint: ConstraintModel, val declarations: FileDeclarationsModel)

    fun locate(path: Path): LocatedPath? {
        val normalized = path.normalize()
        val root = roots.firstOrNull { normalized.startsWith(it.path) && normalized != it.path } ?: return null
        return LocatedPath(root.snapshot, root.path, root.path.relativize(normalized).joinToString("/"))
    }

    fun forPath(path: Path, freshness: Freshness): FileRoles? {
        val located = locate(path) ?: return null
        val order = orderOf(located)
        val match = matches.computeIfAbsent(order to located.relativePath) { (_, relative) -> matcherOf(order, located).matchFile(relative) }
        return match.within(located.snapshot.module, located.projectRoot, located.relativePath, freshness)
    }

    fun fileDeclarationsOf(path: Path, freshness: Freshness): List<RoleFileDeclarations> {
        val located = locate(path) ?: return emptyList()
        val byFile = explained.computeIfAbsent(orderOf(located)) { explainedFilesOf(located.snapshot) }
        return byFile[located.relativePath].orEmpty().map { RoleFileDeclarations(it.role, it.constraint, it.declarations, freshness) }
    }

    private fun orderOf(located: LocatedPath): Int = roots.first { it.snapshot === located.snapshot }.order

    private fun matcherOf(order: Int, located: LocatedPath): DefinitionMatcher =
        matchers.computeIfAbsent(order) { DefinitionMatcher(located.snapshot.definition ?: error("located only among definitions")) }

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
