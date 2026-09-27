package me.tbsten.katachi.dsl.internal

import me.tbsten.katachi.InternalKatachiApi
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.KatachiFileConstraintDirectOnlyCoversNothingException
import me.tbsten.katachi.dsl.KatachiGlobSyntaxException
import me.tbsten.katachi.dsl.LayoutEntry
import me.tbsten.katachi.dsl.LayoutEntryKind
import me.tbsten.katachi.dsl.LayoutScopeImpl
import me.tbsten.katachi.dsl.ModuleResolver
import me.tbsten.katachi.dsl.Role

/**
 * Evaluates every `layout { }` block of every role and flattens them into one list.
 *
 * This is the first step of the check and the only place the deferred blocks are run.
 * Entries come out in declaration order — roles in the order they were declared, and within
 * a role a directory before the entries below it.
 *
 * A role that declares the same path twice, which `/` chains sharing a prefix do all the
 * time, contributes one entry for it. Two *different* roles claiming the same path each
 * contribute their own entry: that is allowed, and both show up in the generated
 * documentation.
 *
 * Public only for katachi's own samples, which pin their layout down in a snapshot with it.
 *
 * ## Example 1: Read every path a definition declares, without touching a project
 * ```kt
 * import me.tbsten.katachi.dsl.kotlin.ktFile
 *
 * val projectArchitecture = architecture {
 *     "domain".group {
 *         "UseCase" { layout { "useCase" / "*UseCase".ktFile() } }
 *     }
 * }
 *
 * val declaredPaths = projectArchitecture.flattenLayout().map { it.path }
 * ```
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

/**
 * Evaluates this role's `layout { }` blocks. See [Architecture.flattenLayout].
 *
 * ## Example 1: Read one role's declared paths on their own
 * ```kt
 * import me.tbsten.katachi.dsl.kotlin.ktFile
 *
 * val projectArchitecture = architecture {
 *     "domain".group {
 *         "UseCase" { layout { "useCase" / "*UseCase".ktFile() } }
 *     }
 * }
 *
 * val useCase = projectArchitecture.allRoles.single { it.name == "UseCase" }
 * val declaredPaths = useCase.flattenLayout().map { it.path }
 * ```
 */
internal fun Role.flattenLayout(
    moduleIndex: ModuleIndex = ModuleIndex.unresolved(ModuleResolver.Conventional),
): List<LayoutEntry> = evaluateLayout(moduleIndex).entries

/**
 * What running the deferred blocks produced: where files may live, and what is asked of them.
 *
 * The two come out of one evaluation because they cannot be worked out apart. Which files a
 * constraint covers *is* the set of entries the block around it declared, so evaluating the
 * blocks twice would be the same work done twice and one more chance for the two answers to
 * stop agreeing — the same reason the walk answers its own two questions at once.
 *
 * It also keeps each role's own evaluation, and whether that one reached a wildcard module key,
 * so that the next evaluation of the same definition can take over every role whose answer
 * cannot differ. See [Architecture.evaluateLayout].
 */
internal class LayoutEvaluation(
    val entries: List<LayoutEntry>,
    val fileConstraints: List<DeclaredFileConstraint>,
    /** The index's resolver, which is everything but the module list a layout reads from it. */
    val resolver: ModuleResolver,
    /**
     * Whether a layout key with a wildcard was expanded -- the only point where the answer
     * depends on which modules the index listed. See [ModuleIndex.reportingWildcardKeys].
     */
    val readsModuleList: Boolean,
    /** Each role's own evaluation, in [Architecture.allRoles] order. Empty for one role's. */
    val byRole: Map<Role, LayoutEvaluation> = emptyMap(),
)

/**
 * [flattenLayout], with the constraints kept.
 *
 * A role that [reusing] evaluated without reaching a wildcard module key is taken from it
 * rather than evaluated again, provided both indexes share the resolver: that role's answer
 * cannot depend on which modules an index listed. This is what spares one run a second
 * evaluation of every `layout { }` block, when both the declarations alone and the walk want
 * them. A role that did reach a wildcard key is evaluated again, because there the two
 * readings really do differ.
 */
internal fun Architecture.evaluateLayout(
    moduleIndex: ModuleIndex,
    reusing: LayoutEvaluation? = null,
): LayoutEvaluation {
    val reusable = reusing?.takeIf { it.resolver == moduleIndex.resolver }?.byRole.orEmpty()
    // `Role` declares no `equals`, so this is keyed by identity, as the walk's own map is.
    val byRole = LinkedHashMap<Role, LayoutEvaluation>()
    for (role in allRoles) {
        byRole[role] = reusable[role]?.takeUnless { it.readsModuleList } ?: role.evaluateLayout(moduleIndex)
    }
    val evaluated = byRole.values
    return LayoutEvaluation(
        entries = evaluated.flatMap { it.entries },
        fileConstraints = evaluated.flatMap { it.fileConstraints },
        resolver = moduleIndex.resolver,
        readsModuleList = evaluated.any { it.readsModuleList },
        byRole = byRole,
    )
}

/** [Role.flattenLayout], with the constraints kept. */
internal fun Role.evaluateLayout(moduleIndex: ModuleIndex): LayoutEvaluation {
    var readsModuleList = false
    val index = moduleIndex.reportingWildcardKeys { readsModuleList = true }
    val entries = LinkedHashMap<EntryKey, LayoutEntry>()
    // Which entry each node produced. `LayoutNode` overrides neither `equals` nor `hashCode`,
    // so this is keyed by identity — which is what is wanted: two sibling declarations of the
    // same name are two nodes that happen to fold into one entry, not one node.
    val byNode = mutableMapOf<LayoutNode, EntryKey>()
    val roots = mutableListOf<LayoutNode>()
    val sites = mutableListOf<FileConstraintSite>()

    for (declaration in layouts) {
        val root = LayoutNode(segment = "", declaredAt = declaration.declaredAt, isFile = false)
        // A layout katachi ships has no frame of the user's in it while it runs, so every line
        // it declares is pinned to where its `layout { }` was declared: the user's call that
        // brought the role in, rather than whoever happened to start the check.
        val pinnedSite = declaration.declaredAt.takeIf { isWrittenByKatachi(declaration.block) }
        val scope = LayoutScopeImpl(root, index, moduleContext = null, sites = sites, pinnedSite = pinnedSite)
        scope.apply(declaration.block)
        // Directly under `layout { }` there is no directory block to close the site, so the
        // root closes it: such a constraint owns that one block and not the role's others.
        scope.closeSite(owned = listOf(root), anchor = null)
        collectInto(entries, root, emptyList(), this, byNode, modulePath = null, inModule = emptyList())
        roots += root
    }

    return LayoutEvaluation(
        entries = entries.values.toList(),
        fileConstraints = buildList {
            // The role's own constraints first: they are the widest thing said about it, and
            // they are written above the `layout { }` blocks in the definition too.
            addAll(declaredFileConstraintsOf(this@evaluateLayout, fileConstraints, roots, null, entries, byNode))
            for (site in sites) {
                addAll(
                    declaredFileConstraintsOf(
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
        resolver = moduleIndex.resolver,
        readsModuleList = readsModuleList,
    )
}

/** A path claimed by the same role twice as the same kind of thing is one entry. */
private data class EntryKey(val path: String, val kind: LayoutEntryKind)

/**
 * What a module package is written as in [LayoutEntry.pathInModule].
 *
 * `**` and not the resolved directory, because the package is a strategy: one `modulePackage`
 * stands for a different directory in every module it is evaluated for, and a reader of one
 * role's page is being shown the declaration rather than one module's expansion of it.
 */
private const val MODULE_PACKAGE_PLACEHOLDER: String = "**"

private fun collectInto(
    entries: MutableMap<EntryKey, LayoutEntry>,
    node: LayoutNode,
    prefix: List<String>,
    role: Role,
    byNode: MutableMap<LayoutNode, EntryKey>,
    modulePath: String?,
    inModule: List<String>,
) {
    for (child in node.children) {
        val segments = prefix + child.segment
        val childModulePath = child.modulePath ?: modulePath
        val childInModule = when {
            // The directory a module key opened is the module itself, so the path inside it
            // starts over from there.
            child.place -> emptyList()
            // Consecutive levels of one package are one placeholder: `com/example/core/domain`
            // is four nodes and one `modulePackage`.
            child.modulePackage ->
                if (inModule.lastOrNull() == MODULE_PACKAGE_PLACEHOLDER) {
                    inModule
                } else {
                    inModule + MODULE_PACKAGE_PLACEHOLDER
                }

            else -> inModule + child.segment
        }
        val entry = child.toEntry(
            path = segments.joinToString("/"),
            role = role,
            modulePath = childModulePath,
            pathInModule = childInModule.joinToString("/"),
        )
        val key = EntryKey(entry.path, entry.kind)
        entries[key] = entries[key]?.mergedWith(entry) ?: entry
        byNode[child] = key
        collectInto(entries, child, segments, role, byNode, childModulePath, childInModule)
    }
}

/**
 * Turns the constraints of one block into what the check evaluates.
 *
 * Every constraint of one block shares the same covered entries and the same anchor, so the
 * work is done once and handed to each of them.
 */
private fun declaredFileConstraintsOf(
    role: Role,
    declarations: List<FileConstraintDeclaration>,
    owned: List<LayoutNode>,
    anchor: LayoutNode?,
    entries: Map<EntryKey, LayoutEntry>,
    byNode: Map<LayoutNode, EntryKey>,
): List<DeclaredFileConstraint> {
    if (declarations.isEmpty()) return emptyList()
    val covered = coveredEntries(owned, entries, byNode)
    val anchorPath = anchor?.let { byNode[it]?.path }
    val paths = anchorsOf(anchorPath, covered)
    val coverage = coverageOf(covered)
    // Worked out only when a block asked for it: most blocks never do.
    val directCoverage by lazy(LazyThreadSafetyMode.NONE) {
        val direct = directEntries(owned, entries, byNode)
        if (direct.none { it.kind == LayoutEntryKind.File || it.kind == LayoutEntryKind.AnyFile }) {
            null
        } else {
            coverageOf(direct)
        }
    }
    return declarations.map { declaration ->
        DeclaredFileConstraint(
            role = role,
            paths = paths,
            layoutPath = anchorPath,
            name = declaration.name,
            declaredAt = declaration.declaredAt,
            coverage = if (declaration.directOnly) {
                directCoverage ?: throw KatachiFileConstraintDirectOnlyCoversNothingException(
                    name = declaration.name,
                    layoutPath = anchorPath ?: "",
                    declaredAt = declaration.declaredAt,
                )
            } else {
                coverage
            },
            check = declaration.check,
        )
    }
}

private fun coverageOf(covered: List<LayoutEntry>): FileConstraintCoverage = FileConstraintCoverage(
    fileGlobs = covered.filter { it.kind == LayoutEntryKind.File }.map { it.glob },
    anyFileGlobs = covered.filter { it.kind == LayoutEntryKind.AnyFile }.map { it.glob },
)

/**
 * The entries a `directOnly` constraint covers: [owned] themselves, whose `anyFile()` opens
 * exactly the files directly inside them, and the files declared as their immediate children.
 *
 * A child *directory* is left out even when it is `anyFile()`, because what it opens is a level
 * further down. One node is one path segment, so `"ksp" / "*".ktFile()` and
 * `"ksp" { "*".ktFile() }` are the same tree and are both left out.
 */
private fun directEntries(
    owned: List<LayoutNode>,
    entries: Map<EntryKey, LayoutEntry>,
    byNode: Map<LayoutNode, EntryKey>,
): List<LayoutEntry> {
    val keys = LinkedHashSet<EntryKey>()
    for (node in owned) {
        if (!node.synthetic) byNode[node]?.let { keys += it }
        for (child in node.children) {
            if (child.isFile && !child.synthetic) byNode[child]?.let { keys += it }
        }
    }
    return keys.mapNotNull { entries[it] }
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

private fun LayoutNode.toEntry(
    path: String,
    role: Role,
    modulePath: String?,
    pathInModule: String,
): LayoutEntry {
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
        synthetic = synthetic,
        place = place,
        modulePath = modulePath,
        pathInModule = pathInModule,
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
 *
 * [LayoutEntry.synthetic] is ANDed rather than kept from either side: a role that wrote
 * `"build.gradle.kts".file()` itself, alongside what `.module { }` injected at the same path,
 * declared that path on its own account and it is no longer only the sugar's doing.
 *
 * [LayoutEntry.place] is ORed, for the mirror-image reason: a path a role reached twice, once as
 * a plain directory on the way somewhere and once by opening a `module { }` block on it, does
 * have a block to write a `description = "..."` in.
 */
private fun LayoutEntry.mergedWith(other: LayoutEntry): LayoutEntry = LayoutEntry(
    path = path,
    glob = glob,
    kind = kind,
    required = required || other.required,
    role = role,
    declaredAt = declaredAt,
    description = description ?: other.description,
    synthetic = synthetic && other.synthetic,
    place = place || other.place,
    // Kept from the first declaration, as the description above is: a path two blocks reached
    // was written once in each of them, and the first one is the one a report already points at.
    modulePath = modulePath ?: other.modulePath,
    pathInModule = pathInModule,
)
