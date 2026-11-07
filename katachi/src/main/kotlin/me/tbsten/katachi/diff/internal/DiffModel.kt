package me.tbsten.katachi.diff.internal

import kotlin.math.ceil
import me.tbsten.katachi.check.internal.LayoutIndex
import me.tbsten.katachi.diff.DiffWeight
import me.tbsten.katachi.diff.KatachiInvalidDefinitionDiffWeightException
import me.tbsten.katachi.diff.KatachiInvalidDiffWeightException
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.Group
import me.tbsten.katachi.dsl.LayoutEntry
import me.tbsten.katachi.dsl.LayoutEntryKind
import me.tbsten.katachi.dsl.Role
import me.tbsten.katachi.dsl.Title

internal class DiffReportModel(
    val range: DiffRange,
    val nodes: List<DiffNode>,
    /** Changed files no role allows, outside every ignored directory: the architecture test fails on them. */
    val unassigned: List<FileChange>,
    val changes: List<FileChange>,
    /** The changed files the per-file listings show; the summary always counts every file. */
    val shown: Set<FileChange> = changes.toSet(),
    /** The limit [shown] was cut to, or 0 for none. */
    val maxFiles: Int = 0,
    /** The weighted diff lines at which a row's importance is full; every row is measured against it. */
    val fullImportanceLines: Int = DEFAULT_FULL_IMPORTANCE_LINES,
    /** Changed files under a directory the layout `ignore()`s: no role owns them, and the architecture test does not look at them. */
    val ignored: List<FileChange> = emptyList(),
) {
    /** How many changed files the per-file listings leave out. */
    val hiddenCount: Int get() = changes.size - shown.size
    val added: Int get() = changes.sumOf { it.added }
    val deleted: Int get() = changes.sumOf { it.deleted }
}

internal sealed class DiffNode {
    abstract val name: String
    abstract val qualifiedName: String
    abstract val title: String?
    abstract val added: Int
    abstract val deleted: Int

    /** Changed lines multiplied by the weight that applies; the heat is read from this. */
    abstract val score: Double
    abstract val fileCount: Int
    val total: Int get() = added + deleted
    val changed: Boolean get() = fileCount > 0

    class GroupNode(val group: Group, val children: List<DiffNode>) : DiffNode() {
        override val name: String get() = group.name
        override val qualifiedName: String get() = group.qualifiedName
        override val title: String? get() = group[Title]
        override val added: Int = children.sumOf { it.added }
        override val deleted: Int = children.sumOf { it.deleted }
        override val score: Double = children.sumOf { it.score }
        override val fileCount: Int = children.sumOf { it.fileCount }
    }

    class RoleNode(val role: Role, val weight: Double, val files: List<FileDiff>) : DiffNode() {
        override val name: String get() = role.name
        override val qualifiedName: String get() = role.qualifiedName
        override val title: String? get() = role[Title]
        override val added: Int = files.sumOf { it.change.added }
        override val deleted: Int = files.sumOf { it.change.deleted }
        override val score: Double = files.sumOf { it.change.total } * weight
        override val fileCount: Int get() = files.size
    }

    fun roles(): List<RoleNode> = when (this) {
        is RoleNode -> listOf(this)
        is GroupNode -> children.flatMap { it.roles() }
    }
}

internal class FileDiff(val change: FileChange, val shortPath: String)

/** 0 for no change, then 1 to [HEAT_LEVELS] out of [full]; a score past [full] stays at the top level. */
internal fun heatLevel(score: Double, full: Int): Int =
    if (score <= 0.0 || full <= 0) 0 else ceil(minOf(score, full.toDouble()) / full * HEAT_LEVELS).toInt().coerceIn(1, HEAT_LEVELS)

internal const val HEAT_LEVELS: Int = 5

internal const val DEFAULT_FULL_IMPORTANCE_LINES: Int = 400

internal fun buildDiffReportModel(
    architecture: Architecture,
    entries: List<LayoutEntry>,
    range: DiffRange,
    changes: List<FileChange>,
    weights: List<String>,
    maxFiles: Int = 0,
    fullImportanceLines: Int = DEFAULT_FULL_IMPORTANCE_LINES,
): DiffReportModel {
    val weightByName = parseWeights(architecture, weights)
    val definitionWeights = definitionWeights(architecture)
    val index = LayoutIndex(entries)
    val entriesByRole = entries.groupBy { it.role }
    val filesByRole = LinkedHashMap<Role, MutableList<FileDiff>>()
    val unassigned = mutableListOf<FileChange>()
    val ignored = mutableListOf<FileChange>()
    for (change in changes) {
        val candidates = listOfNotNull(change.path, change.previousPath)
        val (path, roles) = candidates.firstNotNullOfOrNull { path ->
            rolesOf(index, path).takeIf { it.isNotEmpty() }?.let { path to it }
        } ?: run {
            // Where the file is now decides: one moved out of an ignored directory is one the test now looks at.
            if (isUnderIgnored(index, change.path)) ignored += change else unassigned += change
            continue
        }
        for (role in roles) {
            filesByRole.getOrPut(role) { mutableListOf() } +=
                FileDiff(change, shortPathOf(path, entriesByRole[role].orEmpty()))
        }
    }

    fun roleNode(role: Role) = DiffNode.RoleNode(
        role = role,
        weight = weightOf(role, weightByName) ?: weightOf(role, definitionWeights) ?: 1.0,
        files = filesByRole[role].orEmpty().sortedByDescending { it.change.total },
    )

    fun groupNode(group: Group): DiffNode.GroupNode =
        DiffNode.GroupNode(group, group.roles.map(::roleNode) + group.groups.map(::groupNode))

    val nodes = architecture.roles.map(::roleNode) + architecture.groups.map(::groupNode)
    return DiffReportModel(
        range = range,
        nodes = nodes,
        unassigned = unassigned,
        changes = changes,
        shown = shownFiles(changes, nodes, maxFiles),
        maxFiles = maxFiles,
        fullImportanceLines = fullImportanceLines,
        ignored = ignored,
    )
}

// The files with the most weighted diff lines, then by path, so the same change keeps the same cut.
// A file several roles allow counts once, weighed by the heaviest of them; a file no role allows, ignored or not, weighs 1.
private fun shownFiles(changes: List<FileChange>, nodes: List<DiffNode>, maxFiles: Int): Set<FileChange> {
    if (maxFiles <= 0 || changes.size <= maxFiles) return changes.toSet()
    val weightOf = HashMap<FileChange, Double>()
    for (role in nodes.flatMap { it.roles() }) {
        for (file in role.files) weightOf.merge(file.change, role.weight, ::maxOf)
    }
    return changes
        .sortedWith(compareByDescending<FileChange> { it.total * (weightOf[it] ?: 1.0) }.thenBy { it.path })
        .take(maxFiles)
        .toSet()
}

private fun rolesOf(index: LayoutIndex, path: String): List<Role> =
    if (isUnderIgnored(index, path)) emptyList() else index.rolesOf(path)

// Whether a directory above the file is one `ignore()` leaves out, as the architecture test's walk skips it.
private fun isUnderIgnored(index: LayoutIndex, path: String): Boolean {
    val segments = path.split('/')
    return (1 until segments.size).any { index.isIgnored(segments.take(it).joinToString("/")) }
}

// The most specific name wins: a role's own weight over its group's, a nested group's over its parent's.
// Null when no name on the way up has one, so the next source of weights gets its turn.
private fun weightOf(role: Role, weights: Map<String, Double>): Double? {
    weights[role.qualifiedName]?.let { return it }
    for (depth in role.groupPath.size downTo 1) {
        weights[role.groupPath.take(depth).joinToString(".")]?.let { return it }
    }
    return null
}

// `diffWeight` as written on the groups and roles, by qualified name.
private fun definitionWeights(architecture: Architecture): Map<String, Double> {
    val declared = architecture.allGroups.mapNotNull { group ->
        group[DiffWeight]?.let { Triple(group.qualifiedName, it, group.declaredAt) }
    } + architecture.allRoles.mapNotNull { role ->
        role[DiffWeight]?.let { Triple(role.qualifiedName, it, role.declaredAt) }
    }
    return declared.associate { (name, weight, declaredAt) ->
        if (weight.isNaN() || weight < 0.0) throw KatachiInvalidDefinitionDiffWeightException(name, weight, declaredAt)
        name to weight
    }
}

private fun parseWeights(architecture: Architecture, entries: List<String>): Map<String, Double> {
    val known = architecture.allGroups.map { it.qualifiedName } + architecture.allRoles.map { it.qualifiedName }
    return entries.filter { it.isNotBlank() }.associate { entry ->
        val name = entry.substringBefore('=', missingDelimiterValue = "").trim()
        val value = entry.substringAfter('=', missingDelimiterValue = "").trim().toDoubleOrNull()
        if (name !in known || value == null || value < 0.0) throw KatachiInvalidDiffWeightException(entry, known)
        name to value
    }
}

// Keeps the levels a wildcard of the matching pattern stood for and folds each run of literal
// levels into one "…": the literal ones are the same for every file of the role.
private fun shortPathOf(path: String, roleEntries: List<LayoutEntry>): String {
    val segments = path.split('/')
    val starts = segments.runningFold(0) { offset, segment -> offset + segment.length + 1 }
    val variable = BooleanArray(segments.size)
    variable[segments.lastIndex] = true
    val ranges = roleEntries.firstNotNullOfOrNull { entry ->
        when (entry.kind) {
            LayoutEntryKind.File -> entry.glob.matchRanges(path)
            LayoutEntryKind.AnyFile -> entry.glob.matchRanges(path.substringBeforeLast('/', ""))
            else -> null
        }
    } ?: return path
    for (range in ranges.filterNotNull()) {
        if (range.isEmpty()) continue
        segments.indices.forEach { i ->
            val segmentRange = starts[i] until starts[i] + segments[i].length
            if (range.first <= segmentRange.last && segmentRange.first <= range.last) variable[i] = true
        }
    }
    return buildList {
        segments.forEachIndexed { i, segment ->
            when {
                variable[i] -> add(segment)
                lastOrNull() != ELLIPSIS -> add(ELLIPSIS)
            }
        }
    }.joinToString("/")
}

private const val ELLIPSIS: String = "…"
