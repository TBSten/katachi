package me.tbsten.katachi.dsl.internal

import me.tbsten.katachi.InternalKatachiApi
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.FileConstraintRange
import me.tbsten.katachi.dsl.KatachiConflictingLayoutMetadataException
import me.tbsten.katachi.dsl.KatachiDerivedCaptureWithoutSourceException
import me.tbsten.katachi.dsl.KatachiDuplicateTemplateException
import me.tbsten.katachi.dsl.KatachiDuplicateTemplateIdException
import me.tbsten.katachi.dsl.KatachiFileConstraintDirectOnlyCoversNothingException
import me.tbsten.katachi.dsl.KatachiGlobSyntaxException
import me.tbsten.katachi.dsl.KatachiMissingFirstTemplateException
import me.tbsten.katachi.dsl.KatachiMissingTemplateIdException
import me.tbsten.katachi.dsl.KatachiTemplateOnWildcardException
import me.tbsten.katachi.dsl.LayoutEntry
import me.tbsten.katachi.dsl.LayoutEntryKind
import me.tbsten.katachi.dsl.LayoutScopeImpl
import me.tbsten.katachi.dsl.MetadataKey
import me.tbsten.katachi.dsl.ModuleResolver
import me.tbsten.katachi.dsl.Role

/**
 * Evaluates every `layout { }` block of every role and flattens them into one list.
 *
 * The `layout { }` blocks run here, so a mistake in one is thrown from this call. Entries come out in declaration order — roles in the order they were declared, and within
 * a role a directory before the entries below it.
 *
 * A role that declares the same path twice, which `/` chains sharing a prefix do all the
 * time, contributes one entry for it. Two *different* roles claiming the same path each
 * contribute their own entry: that is allowed, and both show up in the generated
 * documentation.
 *
 * ## Example 1: Read every path a definition declares, without touching a project
 * ```kt
 * import me.tbsten.katachi.dsl.kotlin.ktFile
 *
 * val projectArchitecture = architecture {
 *     "domain".group {
 *         "UseCase".role { layout { "useCase" / "*UseCase".ktFile() } }
 *     }
 * }
 *
 * val declaredPaths = projectArchitecture.flattenLayout().map { it.path }
 * ```
 *
 * @param moduleIndex the project's modules, which wildcard module paths are expanded against.
 *   By default no module is listed, so a wildcard module path stays as written; pass a real
 *   [ModuleIndex] to expand it to the modules that exist.
 * @throws KatachiGlobSyntaxException when a layout key cannot be read as a path pattern.
 */
@InternalKatachiApi
public fun Architecture.flattenLayout(
    moduleIndex: ModuleIndex = ModuleIndex.unresolved(moduleResolver),
): List<LayoutEntry> = evaluateLayout(moduleIndex).entries

internal fun Role.flattenLayout(
    moduleIndex: ModuleIndex = ModuleIndex.unresolved(ModuleResolver.Conventional),
): List<LayoutEntry> = evaluateLayout(moduleIndex).entries

// One evaluation yields both entries and constraints: evaluating twice would duplicate work and risk the two
// answers diverging.
internal class LayoutEvaluation(
    val entries: List<LayoutEntry>,
    val fileConstraints: List<DeclaredFileConstraint>,
    val resolver: ModuleResolver,
    // Only an expanded wildcard key makes the answer depend on the module list; see
    // ModuleIndex.reportingWildcardKeys.
    val readsModuleList: Boolean,
    val byRole: Map<Role, LayoutEvaluation> = emptyMap(),
)

// A role evaluated without reaching a wildcard module key is reused from `reusing` when resolvers match: its
// answer cannot depend on the module list.
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
    val entries = evaluated.flatMap { it.entries }
    if (allTemplateSets.isNotEmpty()) {
        // A member is checked against what the definition declares, not against which modules
        // this project has: a role under a wildcard module key expands to none of them in a
        // project that has no such module yet, and a set naming its template would then be
        // refused for the project's state rather than for a mistake in the definition.
        val unresolved = ModuleIndex.unresolved(moduleIndex.resolver)
        val declared = byRole.flatMap { (role, evaluation) ->
            if (evaluation.readsModuleList && !moduleIndex.keepsWildcardKeys) role.evaluateLayout(unresolved).entries else evaluation.entries
        }
        requireTemplateSetMembersResolve(allTemplateSets, declared)
    }
    return LayoutEvaluation(
        entries = entries,
        fileConstraints = evaluated.flatMap { it.fileConstraints },
        resolver = moduleIndex.resolver,
        readsModuleList = evaluated.any { it.readsModuleList },
        byRole = byRole,
    )
}

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
        val scope = LayoutScopeImpl(root, index, sites = sites, pinnedSite = pinnedSite)
        scope.apply(declaration.block)
        // Directly under `layout { }` there is no directory block to close the site, so the
        // root closes it: such a constraint owns that one block and not the role's others.
        scope.closeSite(owned = listOf(root), anchor = null)
        collectInto(
            entries = entries,
            node = root,
            prefix = emptyList(),
            role = this,
            byNode = byNode,
            modulePath = null,
            inModule = emptyList(),
            captures = CaptureTrail.EMPTY,
        )
        roots += root
    }

    requireTemplatesAreWellFormed(this, byNode.keys)

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

private data class EntryKey(val path: String, val kind: LayoutEntryKind)

// A name written twice along the path is allowed: it declares that the two levels read the same value.
private class CaptureTrail(
    val pathCaptures: List<PathCapture>,
    val declaredAt: List<DeclarationSite>,
) {
    fun toCaptures(): LayoutCaptures? =
        LayoutCaptures(pathCaptures).takeUnless { it.isEmpty }

    // Asked only for declarations files are matched by, so plain captures below the derived one have been seen.
    fun requireDerivedHaveSources(role: Role, path: String) {
        val plain = pathCaptures.filter { it.transform == null }.map { it.name }
        pathCaptures.forEachIndexed { index, capture ->
            val transform = capture.transform ?: return@forEachIndexed
            if (capture.name in plain) return@forEachIndexed
            throw KatachiDerivedCaptureWithoutSourceException(
                name = capture.name,
                transform = transform.spelling,
                role = role.qualifiedName,
                path = path,
                declaredAt = declaredAt[index],
            )
        }
    }

    companion object {
        val EMPTY: CaptureTrail = CaptureTrail(emptyList(), emptyList())
    }
}

private fun CaptureTrail.enter(child: LayoutNode, segmentIndex: Int): CaptureTrail {
    val captures = child.captureSegment?.captures.orEmpty()
    if (captures.isEmpty()) return this
    return CaptureTrail(
        pathCaptures = pathCaptures + captures.map { PathCapture(segmentIndex, it.wildcardIndex, it.name, it.transform) },
        declaredAt = declaredAt + captures.map { child.declaredAt },
    )
}

private fun collectInto(
    entries: MutableMap<EntryKey, LayoutEntry>,
    node: LayoutNode,
    prefix: List<String>,
    role: Role,
    byNode: MutableMap<LayoutNode, EntryKey>,
    modulePath: String?,
    inModule: List<String>,
    captures: CaptureTrail,
) {
    for (child in node.children) {
        val segments = prefix + child.segment
        val childCaptures = captures.enter(child, prefix.size)
        val childModulePath = child.modulePath ?: modulePath
        val childInModule = when {
            // The directory a module key opened is the module itself, so the path inside it
            // starts over from there.
            child.place -> emptyList()
            else -> inModule + child.segment
        }
        val path = segments.joinToString("/")
        // Only where the check matches files: a derived capture on a directory on the way down
        // may find its plain capture further below.
        if (child.entryKind() == LayoutEntryKind.File || child.entryKind() == LayoutEntryKind.AnyFile) {
            childCaptures.requireDerivedHaveSources(role, path)
        }
        val entry = child.toEntry(
            path = path,
            role = role,
            modulePath = childModulePath,
            pathInModule = childInModule.joinToString("/"),
            captures = childCaptures.toCaptures(),
        )
        val key = EntryKey(entry.path, entry.kind)
        entries[key] = entries[key]?.mergedWith(entry) ?: entry
        byNode[child] = key
        collectInto(entries, child, segments, role, byNode, childModulePath, childInModule, childCaptures)
    }
}

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
            coverage = if (declaration.scope == FileConstraintRange.DirectOnly) {
                directCoverage ?: throw KatachiFileConstraintDirectOnlyCoversNothingException(
                    name = declaration.name,
                    layoutPath = anchorPath ?: "",
                    declaredAt = declaration.declaredAt,
                )
            } else {
                coverage
            },
            range = declaration.scope,
            check = declaration.check,
        )
    }
}

private fun coverageOf(covered: List<LayoutEntry>): FileConstraintCoverage = FileConstraintCoverage(covered)

// Child directories are left out even when `anyFile()`: that opens a level further down. `"ksp" / "*".ktFile()`
// and `"ksp" { "*".ktFile() }` are one tree.
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

private fun coveredEntries(
    owned: List<LayoutNode>,
    entries: Map<EntryKey, LayoutEntry>,
    byNode: Map<LayoutNode, EntryKey>,
): List<LayoutEntry> {
    val keys = LinkedHashSet<EntryKey>()
    for (node in owned) collectKeys(node, byNode, keys)
    return keys.mapNotNull { entries[it] }
}

// Skip only the injected node, not its key: a user-written `build.gradle.kts` at the same path still counts.
private fun collectKeys(
    node: LayoutNode,
    byNode: Map<LayoutNode, EntryKey>,
    into: MutableSet<EntryKey>,
) {
    if (!node.synthetic) byNode[node]?.let { into += it }
    for (child in node.children) collectKeys(child, byNode, into)
}

// With no anchor path, drop directories nested inside another so a role in two distant places names both, not
// their common ancestor.
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
    captures: LayoutCaptures?,
): LayoutEntry {
    val kind = entryKind()
    val glob = compilePath(path, role, declaredAt)
    duplicateTemplateAttempt?.let { second ->
        val first = metadata[Template]
            ?: throw KatachiMissingFirstTemplateException(path = path, role = role.qualifiedName)
        throw KatachiDuplicateTemplateException(
            role = role.qualifiedName,
            path = path,
            firstDeclaredAt = first.declaredAt,
            declaredAt = second.declaredAt,
        )
    }
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
        captureVariants = listOfNotNull(captures),
        templateCaptures = captures.takeIf { metadata[Template] != null },
        metadata = metadata.build(),
    )
}

private fun LayoutNode.entryKind(): LayoutEntryKind = when {
    isFile -> LayoutEntryKind.File
    // `ignore()` subsumes `anyFile()`: nothing below is checked, the files directly
    // inside included.
    ignored -> LayoutEntryKind.Ignore
    anyFile -> LayoutEntryKind.AnyFile
    else -> LayoutEntryKind.Directory
}

// Layout blocks are deferred, so the stack no longer points at the key; attach where the pattern was written.
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

// synthetic is ANDed (a user-written `build.gradle.kts` at the same path makes it theirs); place is ORed.
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
    captureVariants = mergedCaptureVariants(captureVariants, other.captureVariants),
    // At most one side ever carries a template -- mergedMetadata already turned two into
    // KatachiDuplicateTemplateException -- so whichever side is non-null is templateCaptures
    // itself, not a guess at captureVariants's order. See LayoutEntry.templateCaptures.
    templateCaptures = templateCaptures ?: other.templateCaptures,
    metadata = mergedMetadata(this, other),
)

// Template is compared by identity, so two `.template { }` on one path collide as
// KatachiDuplicateTemplateException.
private fun mergedMetadata(first: LayoutEntry, second: LayoutEntry): MetadataValues =
    first.metadata.mergedWith(second.metadata) { key, firstValue, secondValue ->
        if (key === Template) {
            val firstTemplate = Template.valueOf(firstValue)
            val secondTemplate = Template.valueOf(secondValue)
            throw KatachiDuplicateTemplateException(
                role = first.role.qualifiedName,
                path = first.path,
                firstDeclaredAt = firstTemplate.declaredAt,
                declaredAt = secondTemplate.declaredAt,
            )
        }
        throw KatachiConflictingLayoutMetadataException(
            role = first.role.qualifiedName,
            path = first.path,
            key = key.toString(),
            firstDeclaredAt = first.declaredAt,
            declaredAt = second.declaredAt,
        )
    }

// A declaration naming nothing is kept as NONE once another named something, so template generation sees the
// unnamed `*`.
private fun mergedCaptureVariants(first: List<LayoutCaptures>, second: List<LayoutCaptures>): List<LayoutCaptures> {
    if (first.isEmpty() && second.isEmpty()) return emptyList()
    return (first.ifEmpty { listOf(LayoutCaptures.NONE) } + second.ifEmpty { listOf(LayoutCaptures.NONE) }).distinct()
}

// Checked after flattening has walked every node, once per role, not where `.template { }` is called; a wildcard
// module key's repeats of one LayoutTemplate count once.
private fun requireTemplatesAreWellFormed(role: Role, nodes: Set<LayoutNode>) {
    for (node in nodes) {
        val template = node.metadata[Template] ?: continue
        if (node.hasUnnamedWildcardInChain()) {
            throw KatachiTemplateOnWildcardException(
                role = role.qualifiedName,
                path = node.pathWithCaptureNames(),
                declaredAt = template.declaredAt,
            )
        }
    }
    // Every node a wildcard module key's expansion attached a template to -- distinct
    // `LayoutTemplate` instances that share a `moduleExpansionGroup` -- is one template, not
    // several: see `LayoutTemplate.equals`.
    val templates = nodes.mapNotNull { it.metadata[Template] }.distinct()
    if (templates.size <= 1) return
    val missing = templates.filter { it.id == null }
    if (missing.isNotEmpty()) {
        val first = missing.first()
        throw KatachiMissingTemplateIdException(
            role = role.qualifiedName,
            declaredAt = first.declaredAt,
            otherIds = (templates - first).mapNotNull { it.id },
        )
    }
    // Every template has an id past this point, so pairing each with its own id (rather than
    // grouping by the nullable `LayoutTemplate.id` directly) is what lets the duplicate below be
    // reported with a non-null one.
    val byId: Map<String, List<LayoutTemplate>> = templates
        .mapNotNull { template -> template.id?.let { it to template } }
        .groupBy({ it.first }, { it.second })
    val duplicate = byId.entries.firstOrNull { it.value.size > 1 } ?: return
    throw KatachiDuplicateTemplateIdException(
        role = role.qualifiedName,
        id = duplicate.key,
        firstDeclaredAt = duplicate.value[0].declaredAt,
        declaredAt = duplicate.value[1].declaredAt,
    )
}

// A module key's wildcards can never be named, so any wildcard in a module directory counts as unnamed.
private fun LayoutNode.hasUnnamedWildcardInChain(): Boolean {
    var current: LayoutNode? = this
    while (current != null) {
        val unnamed = if (current.place) {
            GlobLexer.hasGlobWildcard(current.segment)
        } else {
            current.hasOwnUnnamedWildcard()
        }
        if (unnamed) return true
        current = current.parent
    }
    return false
}

// A derived capture counts as named (filled from its plain capture); compared by count, not character by
// character.
private fun LayoutNode.hasOwnUnnamedWildcard(): Boolean {
    if (segment == "**") return true
    // `?`, `[...]` and `{...}` cannot be given a name, so any of them is a wildcard nothing names.
    if (GlobLexer.hasNonCapturingWildcard(segment)) return true
    val namedCount = captureSegment?.captures?.size ?: 0
    return GlobLexer.capturingStarCount(segment) > namedCount
}

private fun LayoutNode.pathWithCaptureNames(): String {
    val segments = generateSequence(this) { it.parent }
        .map { it.captureSegment?.display() ?: it.segment }
        .filter { it.isNotEmpty() }
        .toList()
        .asReversed()
    return segments.joinToString("/")
}
