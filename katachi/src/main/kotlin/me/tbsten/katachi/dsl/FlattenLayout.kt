package me.tbsten.katachi.dsl

import me.tbsten.katachi.InternalKatachiApi

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
 *   against. The default index has not listed the project at all: a key naming one module
 *   still resolves through [Architecture.moduleResolver], because where `:core:data` lives
 *   needs no tree, while a key with a wildcard is kept as the pattern it was written as —
 *   `":feature:*"` contributes one module's worth of entries whose paths still hold the
 *   wildcard, not one module's worth per feature module that happens to exist. Pass the real
 *   index whenever the file system is at hand: expanding a wildcard key to the modules it
 *   matches is what the check needs, and nothing else can do it.
 * @throws KatachiGlobSyntaxException when a layout key cannot be read as a path pattern.
 */
@InternalKatachiApi
public fun Architecture.flattenLayout(
    moduleIndex: ModuleIndex = ModuleIndex.unresolved(moduleResolver),
): List<LayoutEntry> = evaluateLayout(moduleIndex).entries

/** Evaluates this role's `layout { }` blocks. See [Architecture.flattenLayout]. */
@InternalKatachiApi
public fun Role.flattenLayout(
    moduleIndex: ModuleIndex = ModuleIndex.unresolved(ModuleResolver.Conventional),
): List<LayoutEntry> = evaluateLayout(moduleIndex).entries

/**
 * What running the deferred blocks produced: where files may live, and what is asked of them.
 *
 * The two come out of one evaluation because they cannot be worked out apart. Which files a
 * constraint covers *is* the set of entries the block around it declared, so evaluating the
 * blocks twice would be the same work done twice and one more chance for the two answers to
 * stop agreeing — the same reason the walk answers its own two questions at once.
 */
internal class LayoutEvaluation(
    val entries: List<LayoutEntry>,
    val constraints: List<DeclaredConstraint>,
)

/** [flattenLayout], with the constraints kept. */
internal fun Architecture.evaluateLayout(moduleIndex: ModuleIndex): LayoutEvaluation {
    val evaluated = allRoles.map { it.evaluateLayout(moduleIndex) }
    return LayoutEvaluation(
        entries = evaluated.flatMap { it.entries },
        constraints = evaluated.flatMap { it.constraints },
    )
}

/** [Role.flattenLayout], with the constraints kept. */
internal fun Role.evaluateLayout(moduleIndex: ModuleIndex): LayoutEvaluation {
    val entries = LinkedHashMap<EntryKey, LayoutEntry>()
    // Which entry each node produced. `LayoutNode` overrides neither `equals` nor `hashCode`,
    // so this is keyed by identity — which is what is wanted: two sibling declarations of the
    // same name are two nodes that happen to fold into one entry, not one node.
    val byNode = mutableMapOf<LayoutNode, EntryKey>()
    val roots = mutableListOf<LayoutNode>()
    val sites = mutableListOf<ConstraintSite>()

    for (declaration in layouts) {
        val root = LayoutNode(segment = "", declaredAt = declaration.declaredAt, isFile = false)
        val scope = LayoutScopeImpl(root, moduleIndex, moduleContext = null, sites = sites)
        scope.apply(declaration.block)
        // Directly under `layout { }` there is no directory block to close the site, so the
        // root closes it: such a constraint owns that one block and not the role's others.
        scope.closeSite(owned = listOf(root), anchor = null)
        collectInto(entries, root, emptyList(), this, byNode)
        roots += root
    }

    return LayoutEvaluation(
        entries = entries.values.toList(),
        constraints = buildList {
            // The role's own constraints first: they are the widest thing said about it, and
            // they are written above the `layout { }` blocks in the definition too.
            addAll(declaredConstraintsOf(this@evaluateLayout, constraints, roots, null, entries, byNode))
            for (site in sites) {
                addAll(
                    declaredConstraintsOf(
                        role = this@evaluateLayout,
                        declarations = site.declarations,
                        owned = site.owned,
                        anchor = site.anchor,
                        entries = entries,
                        byNode = byNode,
                    ),
                )
            }
        },
    )
}

/** A path claimed by the same role twice as the same kind of thing is one entry. */
internal data class EntryKey(val path: String, val kind: LayoutEntryKind)

private fun collectInto(
    entries: MutableMap<EntryKey, LayoutEntry>,
    node: LayoutNode,
    prefix: List<String>,
    role: Role,
    byNode: MutableMap<LayoutNode, EntryKey>,
) {
    for (child in node.children) {
        val segments = prefix + child.segment
        val entry = child.toEntry(path = segments.joinToString("/"), role = role)
        val key = EntryKey(entry.path, entry.kind)
        entries[key] = entries[key]?.mergedWith(entry) ?: entry
        byNode[child] = key
        collectInto(entries, child, segments, role, byNode)
    }
}

/**
 * Turns the constraints of one block into what the check evaluates.
 *
 * Every constraint of one block shares the same covered entries and the same anchor, so the
 * work is done once and handed to each of them.
 */
private fun declaredConstraintsOf(
    role: Role,
    declarations: List<ConstraintDeclaration>,
    owned: List<LayoutNode>,
    anchor: LayoutNode?,
    entries: Map<EntryKey, LayoutEntry>,
    byNode: Map<LayoutNode, EntryKey>,
): List<DeclaredConstraint> {
    if (declarations.isEmpty()) return emptyList()
    val covered = coveredEntries(owned, entries, byNode)
    val anchorPath = anchor?.let { byNode[it]?.path }
    val paths = anchorsOf(anchorPath, covered)
    val coverage = ConstraintCoverage(
        fileGlobs = covered.filter { it.kind == LayoutEntryKind.File }.map { it.glob },
        anyFileGlobs = covered.filter { it.kind == LayoutEntryKind.AnyFile }.map { it.glob },
    )
    return declarations.map { declaration ->
        DeclaredConstraint(
            role = role,
            paths = paths,
            layoutPath = anchorPath,
            name = declaration.name,
            declaredAt = declaration.declaredAt,
            coverage = coverage,
            check = declaration.check,
        )
    }
}

/** The entries of [owned]'s subtrees, in the order the blocks declared them. */
private fun coveredEntries(
    owned: List<LayoutNode>,
    entries: Map<EntryKey, LayoutEntry>,
    byNode: Map<LayoutNode, EntryKey>,
): List<LayoutEntry> {
    val keys = LinkedHashSet<EntryKey>()
    for (node in owned) collectKeys(node, byNode, keys)
    return keys.mapNotNull { entries[it] }
}

/**
 * Every entry key at or below [node], leaving out what a `module { }` block injected.
 *
 * Only the node is skipped, not the key: a user who wrote `"build.gradle.kts".file()` of their
 * own in the same block declared the same path, and that node is theirs. The key is taken
 * from it, so a path both of them reached is covered.
 */
private fun collectKeys(
    node: LayoutNode,
    byNode: Map<LayoutNode, EntryKey>,
    into: MutableSet<EntryKey>,
) {
    if (!node.synthetic) byNode[node]?.let { into += it }
    for (child in node.children) collectKeys(child, byNode, into)
}

/**
 * The directories a report opens a constraint's block with.
 *
 * A block with a directory of its own is that directory. Without one — a constraint on the
 * role itself, or directly under `layout { }` — the answer is worked out from what it covers:
 * the directories files were declared in, with the ones nested inside another dropped, so
 * that a role living in two distant places names both rather than their common ancestor.
 */
private fun anchorsOf(anchorPath: String?, covered: List<LayoutEntry>): List<String> {
    if (anchorPath != null) return listOf(anchorPath)
    val directories = covered.mapNotNull {
        when (it.kind) {
            LayoutEntryKind.File -> it.path.substringBeforeLast('/', "").ifEmpty { null }
            LayoutEntryKind.AnyFile -> it.path
            else -> null
        }
    }.distinct()
    return directories
        .filter { candidate -> directories.none { it != candidate && candidate.startsWith("$it/") } }
        // A layout of nothing but directories accepts no file anywhere, yet it is still
        // somewhere: the first declared path says more than nothing at all.
        .ifEmpty { covered.map { it.path }.take(1) }
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
