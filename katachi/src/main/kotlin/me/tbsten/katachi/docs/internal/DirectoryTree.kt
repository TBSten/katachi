package me.tbsten.katachi.docs.internal

import me.tbsten.katachi.dsl.Role

private fun directoryTree(roles: List<Role>, placements: Map<Role, List<Placement>>): String {
    // A module keeps the position of the declaration that first named it, rather than a sorted
    // one: reading the pages in the order the definition was written is what lets a reader match
    // the two up.
    val modules = LinkedHashMap<String?, TreeNode>()
    for (role in roles) {
        for (placement in placements[role].orEmpty()) {
            modules.getOrPut(placement.module) { TreeNode() }.put(placement, role.displayName)
        }
    }

    val lines = mutableListOf<TreeLine>()
    for ((module, root) in modules) {
        if (lines.isNotEmpty()) lines += TreeLine(text = "", roles = emptyList())
        if (module != null) lines += TreeLine(text = module, roles = emptyList())
        // A place with no module is a path from the project root, and there is no heading to
        // write above it -- the same answer the role page's table gives by leaving its module
        // column empty.
        root.appendTo(lines, indent = if (module == null) 0 else 1)
    }
    val column = lines.filter { it.roles.isNotEmpty() }.maxOfOrNull { it.text.length + NAME_GAP } ?: 0
    return lines.joinToString("\n") { it.render(column) }
}

internal fun StringBuilder.appendDirectoryTree(
    roles: List<Role>,
    placements: Map<Role, List<Placement>>,
    heading: String,
) {
    val tree = directoryTree(roles, placements)
    if (tree.isEmpty()) return
    append(SECTION_BREAK)
    append(heading)
    append(SECTION_BREAK)
    append("```\n")
    append(tree)
    append("\n```")
}

private const val NAME_GAP: Int = 2

private const val INDENT: String = "  "

private const val ROLE_SEPARATOR: String = ", "

private class TreeLine(val text: String, val roles: List<String>) {
    fun render(column: Int): String =
        if (roles.isEmpty()) text else text.padEnd(column) + roles.joinToString(ROLE_SEPARATOR)
}

private class TreeNode {
    private val children = LinkedHashMap<String, TreeNode>()
    private val roles = mutableListOf<String>()
    private var isDirectory = false

    fun put(placement: Placement, role: String) {
        var node = this
        for (segment in placement.path.split('/')) {
            node = node.children.getOrPut(segment) { TreeNode() }
        }
        node.isDirectory = node.isDirectory || placement.isDirectory
        // A role that declared two patterns under one directory is one role in that directory.
        if (role !in node.roles) node.roles += role
    }

    fun appendTo(lines: MutableList<TreeLine>, indent: Int) {
        for ((name, child) in children) {
            // A directory that holds one thing and is claimed by nobody is written on the same
            // line as what it holds: `src/main/kotlin/**/` rather than five lines of one
            // directory each. Those five say nothing a reader did not already know from the
            // first -- the shape the tree is drawn for only starts where the path branches.
            val segments = StringBuilder(name)
            var node = child
            while (node.roles.isEmpty() && node.children.size == 1) {
                val (onlyName, onlyChild) = node.children.entries.first()
                segments.append('/').append(onlyName)
                node = onlyChild
            }
            lines += TreeLine(
                text = INDENT.repeat(indent) + segments + if (node.namesDirectory()) "/" else "",
                roles = node.roles.toList(),
            )
            node.appendTo(lines, indent + 1)
        }
    }

    private fun namesDirectory(): Boolean = isDirectory || children.isNotEmpty()
}
