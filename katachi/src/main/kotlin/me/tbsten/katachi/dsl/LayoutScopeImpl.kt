package me.tbsten.katachi.dsl

import me.tbsten.katachi.dsl.internal.FileConstraintDeclaration
import me.tbsten.katachi.dsl.internal.FileConstraintSite
import me.tbsten.katachi.dsl.internal.GlobContext
import me.tbsten.katachi.dsl.internal.LayoutModule
import me.tbsten.katachi.dsl.internal.LayoutNode
import me.tbsten.katachi.dsl.internal.ModuleIndex
import me.tbsten.katachi.dsl.internal.ModuleTarget
import me.tbsten.katachi.dsl.internal.ModulePattern
import me.tbsten.katachi.dsl.internal.captureDeclarationSite
import me.tbsten.katachi.dsl.internal.captureToken
import me.tbsten.katachi.dsl.internal.chainUnder
import me.tbsten.katachi.dsl.internal.requireNoCaptureToken
import me.tbsten.katachi.dsl.internal.fileConstraintDeclarationOf
import me.tbsten.katachi.dsl.internal.markSynthetic
import me.tbsten.katachi.dsl.internal.requireValidIdentifier
import me.tbsten.katachi.dsl.kotlin.ktsFile

// `/` re-parents: the right side is evaluated first, then its outermost node is moved under the directory the left
// side created.
internal class LayoutScopeImpl(
    private val container: LayoutNode,
    private val moduleIndex: ModuleIndex,
    // One list per `layout { }`, not per scope: report order is the order sites are closed in, which only exists
    // across the whole block.
    private val sites: MutableList<FileConstraintSite>,
    // null reads the site off the stack; set for a layout katachi wrote itself (see isWrittenByKatachi).
    private val pinnedSite: DeclarationSite? = null,
) : LayoutDirectoryScope, ModuleAwareLayoutScope {
    private val fileConstraints = mutableListOf<FileConstraintDeclaration>()

    override var description: String?
        get() = container.description
        set(value) {
            value?.let { requireNoCaptureToken(it, where = "a description", declaredAt = siteHere()) }
            container.description = value
        }

    override fun anyFile() {
        container.anyFile = true
    }

    override fun ignore() {
        container.ignored = true
    }

    override fun fileConstraint(
        name: String?,
        declaredAt: DeclarationSite,
        scope: FileConstraintRange,
        check: FileConstraint,
    ) {
        name?.let { requireNoCaptureToken(it, where = "a fileConstraint name", declaredAt = declaredAt) }
        // The layout root is the project root, shared by every block of the `layout { }` and by
        // the root project's expansion alike, so "directly in it" would not be this block's files.
        if (scope == FileConstraintRange.DirectOnly && container.parent == null) {
            throw KatachiFileConstraintDirectOnlyWithoutDirectoryException(name = name, declaredAt = declaredAt)
        }
        fileConstraints += fileConstraintDeclarationOf(
            name = name,
            declaredAt = declaredAt,
            scope = scope,
            check = check,
        )
    }

    override operator fun String.invoke(block: LayoutDirectoryScope.() -> Unit): LayoutDirectory {
        val chain = chainUnder(container, this, isFile = false, declaredAt = siteHere())
        val scope = scopeAt(chain.leaf)
        scope.block()
        scope.closeSite(owned = listOf(chain.leaf), anchor = chain.leaf)
        return LayoutDirectory(top = chain.top, leaf = chain.leaf)
    }

    override fun String.file(): LayoutFile {
        val chain = chainUnder(container, this, isFile = true, declaredAt = siteHere())
        return LayoutFile(top = chain.top, leaf = chain.leaf)
    }

    override fun String.ignore(): LayoutDirectory {
        val chain = chainUnder(container, this, isFile = false, declaredAt = siteHere())
        chain.leaf.ignored = true
        return LayoutDirectory(top = chain.top, leaf = chain.leaf)
    }

    override operator fun String.div(child: String): LayoutDirectory {
        val declaredAt = siteHere()
        val left = chainUnder(container, this, isFile = false, declaredAt = declaredAt)
        val right = chainUnder(left.leaf, child, isFile = false, declaredAt = declaredAt)
        return LayoutDirectory(top = left.top, leaf = right.leaf)
    }

    override operator fun String.div(child: LayoutDirectory): LayoutDirectory {
        val left = chainUnder(container, this, isFile = false, declaredAt = siteHere())
        left.leaf.add(child.top)
        return LayoutDirectory(top = left.top, leaf = child.leaf)
    }

    override operator fun String.div(child: LayoutFile): LayoutFile {
        val left = chainUnder(container, this, isFile = false, declaredAt = siteHere())
        left.leaf.add(child.top)
        return LayoutFile(top = left.top, leaf = child.leaf)
    }

    override operator fun LayoutDirectory.div(child: String): LayoutDirectory {
        val right = chainUnder(leaf, child, isFile = false, declaredAt = siteHere())
        return LayoutDirectory(top = this.top, leaf = right.leaf)
    }

    override operator fun LayoutDirectory.div(child: LayoutDirectory): LayoutDirectory {
        leaf.add(child.top)
        return LayoutDirectory(top = this.top, leaf = child.leaf)
    }

    override operator fun LayoutDirectory.div(child: LayoutFile): LayoutFile {
        leaf.add(child.top)
        return LayoutFile(top = this.top, leaf = child.leaf)
    }

    override operator fun LayoutDirectory.invoke(block: LayoutDirectoryScope.() -> Unit): LayoutDirectory {
        val scope = scopeAt(leaf)
        scope.block()
        scope.closeSite(owned = listOf(leaf), anchor = leaf)
        return this
    }

    override fun capture(name: String): String {
        requireValidIdentifier(name, DeclarationKind.Capture, siteHere())
        return captureToken(name)
    }

    override fun capture(name: String, block: LayoutDirectoryScope.() -> Unit): LayoutDirectory =
        capture(name).invoke(block)

    override fun expandModulePath(modulePath: String, block: LayoutDirectoryScope.() -> Unit): LayoutModule {
        val declaredAt = siteHere()
        val pattern = compileModulePath(modulePath, declaredAt)
        // Shared by every target this one key's wildcard expands to (below), so
        // `LayoutTemplate.equals` can tell a wildcard module key's replay of one `.template { }`
        // call apart from an unrelated user loop that happens to share a source line.
        val expansionGroup = Any()
        // Not `expand`: a wildcard key against an index that has not listed the project stands
        // for itself rather than for nothing. See `ModuleIndex.targetsOf`.
        val declared = moduleIndex.targetsOf(pattern).filter { it.readsOneValuePerName() }.flatMap { target ->
            // The root project resolves to the project root itself, which is this scope's
            // own container: an empty directory name would otherwise become an empty level.
            val directory = target.directory
            val isRootProject = directory.isEmpty()
            val moduleDirectory = if (isRootProject) {
                container
            } else {
                chainUnder(container, directory, isFile = false, declaredAt = declaredAt).leaf
                    // One of the places this role may live in. Not the root project: it has no
                    // directory of its own, so `description = "..."` written in its block would
                    // land on the layout root that every other block shares.
                    .also {
                        it.place = true
                        it.modulePath = target.modulePath
                        it.moduleCaptureNames = target.captureNames
                        it.moduleCapturePattern = target.captureNames?.let { pattern.pattern }
                        it.moduleExpansionGroup = expansionGroup
                    }
            }
            val before = moduleDirectory.children.size
            val scope = scopeAt(moduleDirectory)
            scope.expandModuleDefaults()
            scope.block()
            val added = moduleDirectory.children.drop(before)
            if (isRootProject) {
                // No directory carries the module path here, so the nodes the block added carry
                // it themselves. Without this, everything the root project's block declares would
                // read as belonging to no module at all.
                added.forEach { it.modulePath = target.modulePath }
                // The root project has no directory of its own: its block declares straight
                // into the project root, which everything else in the `layout { }` shares. So
                // a constraint written in it owns what this block added and nothing else, and
                // there is no anchor directory to point a report at.
                scope.closeSite(owned = added, anchor = null)
            } else {
                scope.closeSite(owned = listOf(moduleDirectory), anchor = moduleDirectory)
            }
            added
        }
        return LayoutModule(declared)
    }

    // Called at block end: a constraint covers what the block declared, complete only then. A block with no
    // constraint records nothing.
    fun closeSite(owned: List<LayoutNode>, anchor: LayoutNode?) {
        if (fileConstraints.isEmpty()) return
        sites += FileConstraintSite(
            declarations = fileConstraints.toList(),
            owned = owned,
            anchor = anchor,
        )
    }

    private fun expandModuleDefaults() {
        "build".ignore().markSynthetic()
        "build.gradle".ktsFile().markSynthetic()
    }

    private fun scopeAt(node: LayoutNode): LayoutScopeImpl = LayoutScopeImpl(
        container = node,
        moduleIndex = moduleIndex,
        sites = sites,
        pinnedSite = pinnedSite,
    )

    private fun siteHere(): DeclarationSite = pinnedSite ?: captureDeclarationSite()
}

// Layout blocks are deferred, so the stack no longer points at the key; attach where the module path was written.
private fun compileModulePath(key: String, declaredAt: DeclarationSite): ModulePattern =
    try {
        ModulePattern.compile(key, declaredAt)
    } catch (cause: KatachiGlobSyntaxException) {
        throw KatachiGlobSyntaxException(
            pattern = cause.pattern,
            problem = cause.problem,
            context = GlobContext.LayoutModulePath(key = key, declaredAt = declaredAt),
        )
    }

// A name used twice in a key must read one value in the module; a key kept as a pattern passes.
private fun ModuleTarget.readsOneValuePerName(): Boolean {
    val names = captureNames ?: return true
    val byName = names.withIndex().filter { it.value != null }.groupBy({ it.value }, { it.index })
    return byName.values.all { indices -> indices.map { wildcards.getOrNull(it) }.distinct().size == 1 }
}
