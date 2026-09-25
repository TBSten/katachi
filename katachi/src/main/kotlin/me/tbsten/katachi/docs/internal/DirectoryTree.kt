package me.tbsten.katachi.docs.internal

import me.tbsten.katachi.dsl.Role

/**
 * A container's `layout` read along the other axis: for each module, the directories its roles
 * may put files in, and which role each place belongs to.
 *
 * A `layout { }` is written role by role -- every role says where it may live and nothing else --
 * so no single place in a definition answers the question a reader standing in a directory asks.
 * That view is built here instead, by transposing the very rows the role pages show: both come
 * out of [placementsOf], so the two axes of the documentation cannot drift apart.
 *
 * Returned without a fence around it, and empty when no role of the container declared anything.
 */
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

/**
 * The tree of [roles] under [heading], inside a fence — or nothing at all when there is no tree.
 *
 * The role names in it are plain text: a link inside a fenced block is not a link, and every
 * page that writes a tree already links each of these roles somewhere above it.
 */
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

/** What separates the deepest path from the role names, so the names of a tree line up. */
private const val NAME_GAP: Int = 2

/** One level of nesting. Two spaces rather than box drawing: a path may be long already. */
private const val INDENT: String = "  "

/** Several roles at one path is what the spec asks for rather than an error. */
private const val ROLE_SEPARATOR: String = ", "

/** One line of the rendered tree: a path segment, and the roles allowed to live at it. */
private class TreeLine(val text: String, val roles: List<String>) {
    fun render(column: Int): String =
        if (roles.isEmpty()) text else text.padEnd(column) + roles.joinToString(ROLE_SEPARATOR)
}

/**
 * One directory of the tree under construction.
 *
 * Children are keyed by segment so that two roles naming the same directory meet in one node,
 * and ordered by first mention for the same reason the modules are.
 */
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

    /** Whether the trailing `/` is written: `ignore()` and `anyFile()` say so themselves. */
    private fun namesDirectory(): Boolean = isDirectory || children.isNotEmpty()
}
