package me.tbsten.katachi.dsl

import me.tbsten.katachi.InternalKatachiApi
import me.tbsten.katachi.check.Glob
import me.tbsten.katachi.check.GlobContext
import me.tbsten.katachi.check.KatachiGlobSyntaxException
import me.tbsten.katachi.check.ModuleIndex
import me.tbsten.katachi.check.ModuleResolver

/**
 * Evaluates every `layout { }` block of every role and flattens them into one list.
 *
 * This is the first step of the check and the only place the deferred blocks are run.
 * Entries come out in declaration order — roles in the order they were declared, and within
 * a role a directory before the entries below it.
 *
 * A role that declares the same path twice, which `/` chains sharing a prefix do all the
 * time, contributes one entry for it. Two *different* roles claiming the same path each
 * contribute their own entry: that is allowed, and from v0.3 both show up in the generated
 * documentation.
 *
 * @param moduleIndex the project's modules, which `"...".module { }` keys are expanded
 *   against. The default index holds no module at all: a key naming one module still
 *   resolves through [Architecture.moduleResolver], while a key with a wildcard expands to
 *   nothing, so pass the real index whenever the file system is at hand.
 * @throws KatachiGlobSyntaxException when a layout key cannot be read as a path pattern.
 */
@InternalKatachiApi
public fun Architecture.flattenLayout(
    moduleIndex: ModuleIndex = ModuleIndex(moduleResolver, emptyList()),
): List<LayoutEntry> = allRoles.flatMap { it.flattenLayout(moduleIndex) }

/** Evaluates this role's `layout { }` blocks. See [Architecture.flattenLayout]. */
@InternalKatachiApi
public fun Role.flattenLayout(
    moduleIndex: ModuleIndex = ModuleIndex(ModuleResolver.Conventional, emptyList()),
): List<LayoutEntry> {
    val entries = LinkedHashMap<EntryKey, LayoutEntry>()
    for (declaration in layouts) {
        val root = LayoutNode(segment = "", declaredAt = declaration.declaredAt, isFile = false)
        LayoutScopeImpl(root, moduleIndex, moduleContext = null).apply(declaration.block)
        collectInto(entries, root, emptyList(), this)
    }
    return entries.values.toList()
}

/** A path claimed by the same role twice as the same kind of thing is one entry. */
private data class EntryKey(val path: String, val kind: LayoutEntryKind)

private fun collectInto(
    entries: MutableMap<EntryKey, LayoutEntry>,
    node: LayoutNode,
    prefix: List<String>,
    role: Role,
) {
    for (child in node.children) {
        val segments = prefix + child.segment
        val entry = child.toEntry(path = segments.joinToString("/"), role = role)
        val key = EntryKey(entry.path, entry.kind)
        entries[key] = entries[key]?.mergedWith(entry) ?: entry
        collectInto(entries, child, segments, role)
    }
}

private fun LayoutNode.toEntry(path: String, role: Role): LayoutEntry {
    val kind = when {
        isFile -> LayoutEntryKind.File
        // `ignore()` subsumes `anyFile()`: nothing below is checked, the files directly
        // inside included.
        ignored -> LayoutEntryKind.Ignore
        anyFile -> LayoutEntryKind.AnyFile
        else -> LayoutEntryKind.Directory
    }
    val glob = compilePath(path, role, declaredAt)
    return LayoutEntry(
        path = path,
        glob = glob,
        kind = kind,
        required = kind == LayoutEntryKind.File && !optional && !glob.hasWildcard,
        role = role,
        declaredAt = declaredAt,
        description = description,
    )
}

/**
 * Adds where the pattern was written to whatever the glob compiler complained about. The
 * layout blocks are deferred, so this is the first moment a bad key can be noticed at all,
 * and by then the stack no longer points anywhere useful.
 */
private fun compilePath(path: String, role: Role, declaredAt: DeclarationSite): Glob =
    try {
        Glob.compile(path, Glob.PATH_SEPARATOR)
    } catch (cause: KatachiGlobSyntaxException) {
        throw KatachiGlobSyntaxException(
            pattern = cause.pattern,
            problem = cause.problem,
            context = GlobContext.RoleLayoutPath(role = role, path = path, declaredAt = declaredAt),
        )
    }

/**
 * Keeps the first declaration's position and description, and requires the path when any of
 * the declarations did.
 */
private fun LayoutEntry.mergedWith(other: LayoutEntry): LayoutEntry = LayoutEntry(
    path = path,
    glob = glob,
    kind = kind,
    required = required || other.required,
    role = role,
    declaredAt = declaredAt,
    description = description ?: other.description,
)
