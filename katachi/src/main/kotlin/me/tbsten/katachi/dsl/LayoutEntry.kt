package me.tbsten.katachi.dsl

import me.tbsten.katachi.check.Glob
import me.tbsten.katachi.check.GlobContext
import me.tbsten.katachi.check.KatachiGlobSyntaxException
import me.tbsten.katachi.check.ModuleIndex
import me.tbsten.katachi.check.ModuleResolver

/** What a flattened layout entry declares about the path it names. */
@InternalKatachiApi
public enum class LayoutEntryKind {
    /**
     * A directory, `"..." { }`. Its contents are whatever the entries below it declare, so
     * an empty block means nothing may live there.
     */
    Directory,

    /** A file, `"...".file()`. [LayoutEntry.path] may hold `*` or `**`. */
    File,

    /**
     * A directory that also allows any file directly inside it, `anyFile()`. A file one
     * level further down is not covered.
     */
    AnyFile,

    /** A directory nothing below is checked in, at any depth, `ignore()`. */
    Ignore,
}

/**
 * One line of the flattened layout: a path pattern, and the role that claims it.
 *
 * Roles declare where their own files may live and nothing else, so no single place in the
 * DSL holds the whole tree. The check builds that view by flattening every role's
 * `layout { }` into these entries and walking the real tree against them.
 */
@InternalKatachiApi
public class LayoutEntry internal constructor(
    /** Path pattern relative to the project root, `/` separated, never starting with `/`. */
    public val path: String,
    /** [path] compiled. `Glob.hasWildcard` is what made a declaration optional by itself. */
    public val glob: Glob,
    /** What the declaration says about this path. */
    public val kind: LayoutEntryKind,
    /**
     * Whether a missing file at this path is a violation.
     *
     * Only a [LayoutEntryKind.File] is ever required: git does not track an empty
     * directory, so a directory only shows up once it holds a file, and the files it holds
     * report it themselves. A file holding a wildcard, or marked `optional()`, is not
     * required either.
     */
    public val required: Boolean,
    /** The role that declared this path. */
    public val role: Role,
    /** Where inside the `layout { }` block this path was written. */
    public val declaredAt: DeclarationSite,
    /** The `description = "..."` of this directory, when it has one. */
    public val description: String?,
) {
    override fun toString(): String =
        "LayoutEntry($path, $kind, ${role.qualifiedName}${if (required) ", required" else ""})"
}

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
