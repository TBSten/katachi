package me.tbsten.katachi.diff.internal

/**
 * The report as data, for the JSON and the YAML: the same tree the HTML and the Markdown show, group
 * to role to file, with every changed file in it whatever `maxFiles` is.
 */
internal fun diffReportData(model: DiffReportModel): DiffData.Record {
    val full = model.fullImportanceLines
    val roles = model.nodes.flatMap { it.roles() }
    return diffRecord {
        put("schemaVersion", DIFF_DATA_SCHEMA_VERSION)
        put("range", rangeData(model.range))
        put(
            "summary",
            diffRecord {
                put("fileCount", model.changes.size)
                put("added", model.added)
                put("deleted", model.deleted)
                put("roleCount", roles.size)
                put("changedRoleCount", roles.count { it.changed })
                // The commonest question of a script, answered without walking `nodes`.
                put("changedRoles", roles.filter { it.changed }.map { DiffData.Text(it.qualifiedName) })
                put("noRoleFileCount", model.unassigned.size)
                put("ignoredFileCount", model.ignored.size)
                put("fullImportanceLines", full)
                put("maxImportance", HEAT_LEVELS)
            },
        )
        put("nodes", model.nodes.map { nodeData(it, model.range, full) })
        put("noRole", looseFilesData(model.unassigned, model.range, full))
        put("ignored", looseFilesData(model.ignored, model.range, full))
    }
}

private fun rangeData(range: DiffRange): DiffData.Record = diffRecord {
    put("label", range.label)
    put("before", range.before)
    put("after", range.after.takeIf { it.isNotEmpty() })
    put("beforeSha", range.beforeSha)
    put("afterSha", range.afterSha)
    put(
        "pullRequest",
        range.pullRequest?.let { pullRequest ->
            diffRecord {
                put("number", pullRequest.number)
                put("url", pullRequest.url)
            }
        },
    )
    put("repositoryUrl", range.github?.webUrl)
    put("projectPath", range.repositoryPrefix.removeSuffix("/"))
}

private fun nodeData(node: DiffNode, range: DiffRange, full: Int): DiffData.Record = diffRecord {
    put("type", if (node is DiffNode.GroupNode) "group" else "role")
    put("name", node.name)
    put("qualifiedName", node.qualifiedName)
    put("title", node.title)
    put("changed", node.changed)
    put("fileCount", node.fileCount)
    put("added", node.added)
    put("deleted", node.deleted)
    put("score", node.score)
    put("importance", heatLevel(node.score, full))
    when (node) {
        is DiffNode.GroupNode -> put("children", node.children.map { nodeData(it, range, full) })
        is DiffNode.RoleNode -> {
            put("weight", node.weight)
            put("files", node.files.map { fileData(it.change, node.weight, range, full) })
        }
    }
}

// A file no role allows, ignored or not, weighs 1, as it does in the other formats.
private fun looseFilesData(changes: List<FileChange>, range: DiffRange, full: Int): DiffData.Record = diffRecord {
    put("fileCount", changes.size)
    put("added", changes.sumOf { it.added })
    put("deleted", changes.sumOf { it.deleted })
    put("files", changes.map { fileData(it, weight = 1.0, range, full) })
}

private fun fileData(change: FileChange, weight: Double, range: DiffRange, full: Int): DiffData.Record = diffRecord {
    val score = change.total * weight
    put("path", change.path)
    put("previousPath", change.previousPath)
    put("status", change.status.label)
    put("binary", change.binary)
    put("added", change.added)
    put("deleted", change.deleted)
    put("score", score)
    put("importance", heatLevel(score, full))
    put("githubUrl", range.githubUrl(change.path))
    put("gitDiffCommand", range.gitDiffCommand(change))
}

/** Raised when a key is renamed or removed, or a value changes its meaning; adding a key keeps it. */
internal const val DIFF_DATA_SCHEMA_VERSION: Int = 1
