package me.tbsten.katachi.dsl

import me.tbsten.katachi.dsl.internal.FileConstraintDeclaration
import me.tbsten.katachi.dsl.internal.FileConstraintSite
import me.tbsten.katachi.dsl.internal.GlobContext
import me.tbsten.katachi.dsl.internal.LayoutNode
import me.tbsten.katachi.dsl.internal.ModuleContext
import me.tbsten.katachi.dsl.internal.ModuleIndex
import me.tbsten.katachi.dsl.internal.ModulePattern
import me.tbsten.katachi.dsl.internal.captureDeclarationSite
import me.tbsten.katachi.dsl.internal.captureToken
import me.tbsten.katachi.dsl.internal.chainUnder
import me.tbsten.katachi.dsl.internal.requireNoCaptureToken
import me.tbsten.katachi.dsl.internal.fileConstraintDeclarationOf
import me.tbsten.katachi.dsl.internal.markSynthetic
import me.tbsten.katachi.dsl.internal.requireValidIdentifier
import me.tbsten.katachi.dsl.kotlin.ktsFile

/**
 * Implements both receivers of the layout DSL, and [ModuleAwareLayoutScope] with them. The
 * root of a `layout { }` block is handed out as [LayoutScope], which hides `description`,
 * `anyFile()` and `ignore()`; a directory block is handed out as [LayoutDirectoryScope],
 * which shows them. What the scope knows while it is being evaluated — the module at hand,
 * and how to resolve a module path — is reached through [ModuleAwareLayoutScope], so that the
 * utility layer never names this class.
 *
 * `/` works by re-parenting. `"gradle" / "libs.versions.toml".file()` evaluates the right
 * side first, so the file is declared in this scope and then moved under the directory the
 * left side created. Moving the *outermost* node of the right side is what makes a
 * multi-level key such as `"x" / "app/ios" { }` land as `x/app/ios`.
 *
 * Only the core vocabulary is implemented here. `"...".ktFile()` and the Gradle vocabulary
 * are ordinary functions taking a [LayoutScope] as a context parameter, written on top of
 * what this class provides — see [me.tbsten.katachi.dsl.kotlin.ktFile] and the `me.tbsten.katachi.dsl.gradle` package.
 * A source set is `"src/<name>" { }` and a module package is a directory key, so
 * neither needs anything of its own here: that is why `mainSourceSet / kotlin` and
 * `mainSourceSet { kotlin { } }` cannot come apart — they run the same code.
 */
internal class LayoutScopeImpl(
    private val container: LayoutNode,
    private val moduleIndex: ModuleIndex,
    private val moduleContext: ModuleContext?,
    /**
     * Where every block of this one `layout { }` files what it declared, shared by every scope
     * the block creates below it.
     *
     * One list per `layout { }` rather than one per scope, because the order sites are closed
     * in is the order a report reads them in, and that order only exists across the whole
     * block.
     */
    private val sites: MutableList<FileConstraintSite>,
    /**
     * The site every declaration of this block records, or `null` to read it off the stack.
     * Set for a layout katachi itself wrote; see [me.tbsten.katachi.dsl.internal.isWrittenByKatachi].
     */
    private val pinnedSite: DeclarationSite? = null,
) : LayoutDirectoryScope, ModuleAwareLayoutScope {
    /** Constraints written directly in *this* block. A nested block collects its own. */
    private val fileConstraints = mutableListOf<FileConstraintDeclaration>()

    override val currentModulePath: String?
        get() = moduleContext?.modulePath

    override val currentWildcards: List<String>?
        get() = moduleContext?.wildcards

    override val currentCaptures: Map<String, String>?
        get() = moduleContext?.let { context -> context.captureNames?.let { context.captures } }

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
        // `":".module { }` alike, so "directly in it" would not be this block's files.
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

    /** See [me.tbsten.katachi.dsl.gradle.expandModulePath], the opt-in API this backs. */
    override fun expandModulePath(modulePath: String, block: LayoutDirectoryScope.() -> Unit): LayoutModule {
        val declaredAt = siteHere()
        requireLayoutRoot(modulePath, declaredAt)
        val pattern = compileModulePath(modulePath, declaredAt)
        // Shared by every target this one key's wildcard expands to (below), so
        // `LayoutTemplate.equals` can tell a wildcard module key's replay of one `.template { }`
        // call apart from an unrelated user loop that happens to share a source line.
        val expansionGroup = Any()
        // Not `expand`: a wildcard key against an index that has not listed the project stands
        // for itself rather than for nothing. See `ModuleIndex.targetsOf`.
        val moduleDirectories = mutableListOf<LayoutNode>()
        val declared = moduleIndex.targetsOf(pattern).flatMap { target ->
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
                        moduleDirectories += it
                    }
            }
            val before = moduleDirectory.children.size
            val scope = LayoutScopeImpl(
                container = moduleDirectory,
                moduleIndex = moduleIndex,
                moduleContext = ModuleContext(target.modulePath, target.wildcards, target.captureNames),
                sites = sites,
                pinnedSite = pinnedSite,
            )
            scope.expandModuleDefaults()
            scope.block()
            val added = moduleDirectory.children.drop(before)
            if (isRootProject) {
                // No directory carries the module path here, so the nodes the block added carry
                // it themselves. Without this, everything a `":".module { }` declares would read
                // as belonging to no module at all.
                added.forEach { it.modulePath = target.modulePath }
                // `":".module { }` has no directory of its own: its block declares straight
                // into the project root, which everything else in the `layout { }` shares. So
                // a constraint written in it owns what this block added and nothing else, and
                // there is no anchor directory to point a report at.
                scope.closeSite(owned = added, anchor = null)
            } else {
                scope.closeSite(owned = listOf(moduleDirectory), anchor = moduleDirectory)
            }
            added
        }
        return LayoutModule(declared, moduleDirectories)
    }

    /**
     * Records the constraints written in this block, together with what the block owns.
     *
     * Called when the block finishes, because a constraint covers what the block turned out to
     * declare and that is not complete until then. A block that wrote no constraint records
     * nothing: a site with no declaration would only make the evaluation walk subtrees nobody
     * asked about.
     */
    fun closeSite(owned: List<LayoutNode>, anchor: LayoutNode?) {
        if (fileConstraints.isEmpty()) return
        sites += FileConstraintSite(
            declarations = fileConstraints.toList(),
            owned = owned,
            anchor = anchor,
        )
    }

    /**
     * The two lines every Gradle module has. Written through the same vocabulary a user
     * writes, so that what a module block adds is the same declaration a hand written block
     * would make.
     *
     * Both are marked as katachi's own, which keeps them out of what a constraint written in
     * the module block covers. See [LayoutNode.synthetic].
     */
    private fun expandModuleDefaults() {
        "build".ignore().markSynthetic()
        "build.gradle".ktsFile().markSynthetic()
    }

    private fun scopeAt(node: LayoutNode): LayoutScopeImpl = LayoutScopeImpl(
        container = node,
        moduleIndex = moduleIndex,
        moduleContext = moduleContext,
        sites = sites,
        pinnedSite = pinnedSite,
    )

    private fun siteHere(): DeclarationSite = pinnedSite ?: captureDeclarationSite()

    /**
     * A module path is relative to nothing: it is resolved to a directory below the project
     * root. Writing one inside a directory block would quietly put that directory in front
     * of the answer, so it is refused instead.
     */
    private fun requireLayoutRoot(key: String, declaredAt: DeclarationSite) {
        if (container.parent == null && moduleContext == null) return
        throw KatachiModuleOutsideLayoutRootException(modulePath = key, declaredAt = declaredAt)
    }
}

/**
 * Adds where the module path was written to whatever the pattern compiler complained about,
 * and checks that no name its `capture("...")` tokens gave two of its `*`s is used twice.
 *
 * The layout blocks are deferred, so this is the first moment a bad key can be noticed at
 * all, and by then the stack no longer points anywhere useful.
 */
private fun compileModulePath(key: String, declaredAt: DeclarationSite): ModulePattern {
    val pattern = try {
        ModulePattern.compile(key, declaredAt)
    } catch (cause: KatachiGlobSyntaxException) {
        throw KatachiGlobSyntaxException(
            pattern = cause.pattern,
            problem = cause.problem,
            context = GlobContext.LayoutModulePath(key = key, declaredAt = declaredAt),
        )
    }
    val names = pattern.wildcardNames.filterNotNull()
    val duplicate = names.groupingBy { it }.eachCount().entries.firstOrNull { it.value > 1 }
    if (duplicate != null) {
        throw KatachiDuplicateCaptureException(
            name = duplicate.key,
            role = null,
            path = pattern.pattern,
            firstDeclaredAt = declaredAt,
            declaredAt = declaredAt,
        )
    }
    return pattern
}
