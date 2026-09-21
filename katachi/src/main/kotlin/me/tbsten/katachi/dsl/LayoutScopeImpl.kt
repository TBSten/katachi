// Module expansion is written entirely against katachi's own module index.
@file:OptIn(InternalKatachiApi::class)

package me.tbsten.katachi.dsl

import me.tbsten.katachi.check.GlobSyntaxException
import me.tbsten.katachi.check.ModuleIndex
import me.tbsten.katachi.check.ModulePattern

/** The module a block is being evaluated for, or `null` when there is none. */
internal class ModuleContext(
    /** The module path as katachi prints it, `":feature:home"`. */
    val modulePath: String,
    /** What the key's wildcards captured for this module. */
    val wildcards: List<String>,
)

/**
 * Implements both receivers of the layout DSL. The root of a `layout { }` block is handed
 * out as [LayoutScope], which hides `description`, `anyFile()` and `ignore()`; a directory
 * block is handed out as [LayoutDirectoryScope], which shows them.
 *
 * `/` works by re-parenting. `"gradle" / "libs.versions.toml".file()` evaluates the right
 * side first, so the file is declared in this scope and then moved under the directory the
 * left side created. Moving the *outermost* node of the right side is what makes a
 * multi-level key such as `"x" / "app/ios" { }` land as `x/app/ios`.
 *
 * A source set, `kotlin` and a module package are the same thing seen from another angle:
 * each declares a directory in this scope the moment it is read, and hands back the
 * [LayoutDirectory] that `/` and `{ }` continue from. That is why `mainSourceSet / kotlin`
 * and `mainSourceSet { kotlin { } }` cannot come apart — they run the same code.
 */
internal class LayoutScopeImpl(
    private val container: LayoutNode,
    private val moduleIndex: ModuleIndex,
    private val moduleContext: ModuleContext?,
) : LayoutDirectoryScope {
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

    override val wildcards: List<String>
        get() = moduleContext?.wildcards ?: throw KatachiDeclarationException(
            "`wildcards` can only be read inside a `module { }` block. It holds what the " +
                "module path's `*` and `**` captured for the module being evaluated, and " +
                "directly under `layout { }`, or inside a plain directory block, there is no " +
                "module path to have captured anything.",
        )

    override operator fun String.invoke(block: LayoutDirectoryScope.() -> Unit): LayoutDirectory {
        val chain = chainUnder(container, this, isFile = false, declaredAt = captureDeclarationSite())
        scopeAt(chain.leaf).block()
        return LayoutDirectory(top = chain.top, leaf = chain.leaf)
    }

    override fun String.file(): LayoutFile = declareFile(this)

    override fun String.ktFile(): LayoutFile = declareFile("$this.kt")

    override fun String.ktsFile(): LayoutFile = declareFile("$this.kts")

    override fun String.ignore(): LayoutDirectory {
        val chain = chainUnder(container, this, isFile = false, declaredAt = captureDeclarationSite())
        chain.leaf.ignored = true
        return LayoutDirectory(top = chain.top, leaf = chain.leaf)
    }

    override fun String.module(block: LayoutDirectoryScope.() -> Unit): LayoutModule {
        val declaredAt = captureDeclarationSite()
        requireLayoutRoot(this, declaredAt)
        val pattern = compileModulePath(this, declaredAt)
        val declared = moduleIndex.expand(pattern).flatMap { module ->
            // The root project resolves to the project root itself, which is this scope's
            // own container: an empty directory name would otherwise become an empty level.
            val directory = module.directory
            val moduleDirectory = if (directory.isEmpty()) {
                container
            } else {
                chainUnder(container, directory, isFile = false, declaredAt = declaredAt).leaf
            }
            val before = moduleDirectory.children.size
            val scope = LayoutScopeImpl(
                container = moduleDirectory,
                moduleIndex = moduleIndex,
                moduleContext = ModuleContext(module.path.value, module.wildcards),
            )
            scope.expandModuleDefaults()
            scope.block()
            moduleDirectory.children.drop(before)
        }
        return LayoutModule(declared)
    }

    override val String.sourceSet: LayoutDirectory get() = declareDirectory("src/$this")

    override val mainSourceSet: LayoutDirectory get() = declareDirectory("src/main")

    override val testSourceSet: LayoutDirectory get() = declareDirectory("src/test")

    override val kotlin: LayoutDirectory get() = declareDirectory("kotlin")

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

    override operator fun String.div(child: ModulePackage): LayoutDirectory {
        val declaredAt = captureDeclarationSite()
        val left = chainUnder(container, this, isFile = false, declaredAt = declaredAt)
        val right = chainUnder(left.leaf, child.directory(), isFile = false, declaredAt = declaredAt)
        return LayoutDirectory(top = left.top, leaf = right.leaf)
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

    override operator fun LayoutDirectory.div(child: ModulePackage): LayoutDirectory {
        val right = chainUnder(leaf, child.directory(), isFile = false, declaredAt = captureDeclarationSite())
        return LayoutDirectory(top = this.top, leaf = right.leaf)
    }

    override operator fun LayoutDirectory.invoke(block: LayoutDirectoryScope.() -> Unit): LayoutDirectory {
        scopeAt(leaf).block()
        return this
    }

    override operator fun ModulePackage.div(child: String): LayoutDirectory {
        val declaredAt = captureDeclarationSite()
        val left = chainUnder(container, directory(), isFile = false, declaredAt = declaredAt)
        val right = chainUnder(left.leaf, child, isFile = false, declaredAt = declaredAt)
        return LayoutDirectory(top = left.top, leaf = right.leaf)
    }

    override operator fun ModulePackage.div(child: LayoutDirectory): LayoutDirectory {
        val left = chainUnder(container, directory(), isFile = false, declaredAt = captureDeclarationSite())
        left.leaf.add(child.top)
        return LayoutDirectory(top = left.top, leaf = child.leaf)
    }

    override operator fun ModulePackage.div(child: LayoutFile): LayoutFile {
        val left = chainUnder(container, directory(), isFile = false, declaredAt = captureDeclarationSite())
        left.leaf.add(child.top)
        return LayoutFile(top = left.top, leaf = child.leaf)
    }

    override operator fun ModulePackage.invoke(block: LayoutDirectoryScope.() -> Unit): LayoutDirectory {
        val chain = chainUnder(container, directory(), isFile = false, declaredAt = captureDeclarationSite())
        scopeAt(chain.leaf).block()
        return LayoutDirectory(top = chain.top, leaf = chain.leaf)
    }

    /**
     * The two lines every Gradle module has. Written through this scope's own functions, so
     * that what `module { }` adds is the same declaration a hand written block would make.
     */
    private fun expandModuleDefaults() {
        "build".ignore()
        "build.gradle".ktsFile()
    }

    private fun scopeAt(node: LayoutNode): LayoutScopeImpl =
        LayoutScopeImpl(container = node, moduleIndex = moduleIndex, moduleContext = moduleContext)

    private fun declareFile(name: String): LayoutFile {
        val chain = chainUnder(container, name, isFile = true, declaredAt = captureDeclarationSite())
        return LayoutFile(top = chain.top, leaf = chain.leaf)
    }

    private fun declareDirectory(key: String): LayoutDirectory {
        val chain = chainUnder(container, key, isFile = false, declaredAt = captureDeclarationSite())
        return LayoutDirectory(top = chain.top, leaf = chain.leaf)
    }

    /** The package directory of the module being evaluated. */
    private fun ModulePackage.directory(): String = resolveFor(moduleContext?.modulePath)

    /**
     * A module path is relative to nothing: it is resolved to a directory below the project
     * root. Writing one inside a directory block would quietly put that directory in front
     * of the answer, so it is refused instead.
     */
    private fun requireLayoutRoot(key: String, declaredAt: DeclarationSite) {
        if (container.parent == null && moduleContext == null) return
        throw KatachiDeclarationException(
            "`$key`.module { } at $declaredAt is not directly inside `layout { }`. A module " +
                "path is resolved to a directory below the project root, so it cannot be " +
                "nested in another directory. Move it up, or write the directory it lives in " +
                "as a plain key.",
        )
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
    } catch (cause: GlobSyntaxException) {
        throw GlobSyntaxException("The layout declares the module `$key` at $declaredAt. ${cause.message}")
    }
