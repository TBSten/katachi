package me.tbsten.katachi.dsl

import me.tbsten.katachi.dsl.kotlin.ktsFile

/** The module a block is being evaluated for, or `null` when there is none. */
internal class ModuleContext(
    /** The module path as katachi prints it, `":feature:home"`. */
    val modulePath: String,
    /** What the key's wildcards captured for this module. */
    val wildcards: List<String>,
)

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
 * what this class provides — see [ktFile] and the `me.tbsten.katachi.dsl.gradle` package.
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
    private val sites: MutableList<ConstraintSite>,
) : LayoutDirectoryScope, ModuleAwareLayoutScope {
    /** Constraints written directly in *this* block. A nested block collects its own. */
    private val constraints = mutableListOf<ConstraintDeclaration>()

    override val currentModulePath: String?
        get() = moduleContext?.modulePath

    override val currentWildcards: List<String>?
        get() = moduleContext?.wildcards

    override var description: String?
        get() = container.description
        set(value) {
            container.description = value
        }

    override fun anyFile() {
        container.anyFile = true
    }

    override fun ignore() {
        container.ignored = true
    }

    override fun constraint(name: String?, declaredAt: DeclarationSite, check: FileSetConstraint) {
        constraints += constraintDeclarationOf(name = name, declaredAt = declaredAt, check = check)
    }

    override operator fun String.invoke(block: LayoutDirectoryScope.() -> Unit): LayoutDirectory {
        val chain = chainUnder(container, this, isFile = false, declaredAt = captureDeclarationSite())
        val scope = scopeAt(chain.leaf)
        scope.block()
        scope.closeSite(owned = listOf(chain.leaf), anchor = chain.leaf)
        return LayoutDirectory(top = chain.top, leaf = chain.leaf)
    }

    override fun String.file(): LayoutFile {
        val chain = chainUnder(container, this, isFile = true, declaredAt = captureDeclarationSite())
        return LayoutFile(top = chain.top, leaf = chain.leaf)
    }

    override fun String.ignore(): LayoutDirectory {
        val chain = chainUnder(container, this, isFile = false, declaredAt = captureDeclarationSite())
        chain.leaf.ignored = true
        return LayoutDirectory(top = chain.top, leaf = chain.leaf)
    }

    override operator fun String.div(child: String): LayoutDirectory {
        val declaredAt = captureDeclarationSite()
        val left = chainUnder(container, this, isFile = false, declaredAt = declaredAt)
        val right = chainUnder(left.leaf, child, isFile = false, declaredAt = declaredAt)
        return LayoutDirectory(top = left.top, leaf = right.leaf)
    }

    override operator fun String.div(child: LayoutDirectory): LayoutDirectory {
        val left = chainUnder(container, this, isFile = false, declaredAt = captureDeclarationSite())
        left.leaf.add(child.top)
        return LayoutDirectory(top = left.top, leaf = child.leaf)
    }

    override operator fun String.div(child: LayoutFile): LayoutFile {
        val left = chainUnder(container, this, isFile = false, declaredAt = captureDeclarationSite())
        left.leaf.add(child.top)
        return LayoutFile(top = left.top, leaf = child.leaf)
    }

    override operator fun LayoutDirectory.div(child: String): LayoutDirectory {
        val right = chainUnder(leaf, child, isFile = false, declaredAt = captureDeclarationSite())
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

    /** See [me.tbsten.katachi.dsl.gradle.expandModulePath], the opt-in API this backs. */
    override fun expandModulePath(modulePath: String, block: LayoutDirectoryScope.() -> Unit): LayoutModule {
        val declaredAt = captureDeclarationSite()
        requireLayoutRoot(modulePath, declaredAt)
        val pattern = compileModulePath(modulePath, declaredAt)
        // Not `expand`: a wildcard key against an index that has not listed the project stands
        // for itself rather than for nothing. See `ModuleIndex.targetsOf`.
        val declared = moduleIndex.targetsOf(pattern).flatMap { target ->
            // The root project resolves to the project root itself, which is this scope's
            // own container: an empty directory name would otherwise become an empty level.
            val directory = target.directory
            val isRootProject = directory.isEmpty()
            val moduleDirectory = if (isRootProject) {
                container
            } else {
                chainUnder(container, directory, isFile = false, declaredAt = declaredAt).leaf
            }
            val before = moduleDirectory.children.size
            val scope = LayoutScopeImpl(
                container = moduleDirectory,
                moduleIndex = moduleIndex,
                moduleContext = ModuleContext(target.modulePath, target.wildcards),
                sites = sites,
            )
            scope.expandModuleDefaults()
            scope.block()
            val added = moduleDirectory.children.drop(before)
            if (isRootProject) {
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
        return LayoutModule(declared)
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
        if (constraints.isEmpty()) return
        sites += ConstraintSite(
            declarations = constraints.toList(),
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
    )

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
 * Adds where the module path was written to whatever the pattern compiler complained about.
 * The layout blocks are deferred, so this is the first moment a bad key can be noticed at
 * all, and by then the stack no longer points anywhere useful.
 */
private fun compileModulePath(key: String, declaredAt: DeclarationSite): ModulePattern =
    try {
        ModulePattern.compile(key)
    } catch (cause: KatachiGlobSyntaxException) {
        throw KatachiGlobSyntaxException(
            pattern = cause.pattern,
            problem = cause.problem,
            context = GlobContext.LayoutModulePath(key = key, declaredAt = declaredAt),
        )
    }
