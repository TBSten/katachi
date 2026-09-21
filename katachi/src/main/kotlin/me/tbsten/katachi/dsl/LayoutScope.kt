package me.tbsten.katachi.dsl

/**
 * Receiver of `layout { }`, and of every directory block inside it.
 *
 * Directly under `layout { }` the paths are relative to the project root, so a file that
 * belongs to no module is written there as it is:
 *
 * ```kotlin
 * layout {
 *   ".gitignore".file()                        // <root>/.gitignore
 *   "gradle" { "libs.versions.toml".file() }   // <root>/gradle/libs.versions.toml
 *   "src" / "main" / "kotlin" / "*.kt".file()  // the same thing, nested with `/`
 * }
 * ```
 *
 * A key is read one way only: `"..." { }` is a directory, and a file is always written with
 * one of the file functions. A key may itself spell out several levels (`"app/ios" { }`),
 * which is the same as nesting one block per level.
 *
 * [LayoutDirectoryScope] adds the three things that only make sense inside a directory:
 * `description`, `anyFile()` and `ignore()`.
 */
@KatachiDsl
public sealed interface LayoutScope {
    /**
     * Declares a directory. Nest the blocks to walk down the tree.
     *
     * The key may hold more than one level: `"app/ios" { }` means the same as
     * `"app" { "ios" { } }`.
     *
     * An empty block means *nothing may live here*. Use `anyFile()` to allow anything
     * directly inside, and `ignore()` to stop checking below it altogether.
     */
    public operator fun String.invoke(block: LayoutDirectoryScope.() -> Unit): LayoutDirectory

    /** Declares a file, named exactly as written: `"libs.versions.toml".file()`. */
    public fun String.file(): LayoutFile

    /** Declares a file with `.kt` appended: `"*UseCase".ktFile()` is `*UseCase.kt`. */
    public fun String.ktFile(): LayoutFile

    /** Declares a file with `.kts` appended: `"build.gradle".ktsFile()` is `build.gradle.kts`. */
    public fun String.ktsFile(): LayoutFile

    /**
     * Declares a directory and stops the check below it, at any depth.
     *
     * `"build".ignore()` is the short form of `"build" { ignore() }`; the two produce the
     * same declaration.
     */
    public fun String.ignore(): LayoutDirectory

    /** `"src" / "main"` is the same as `"src" { "main" { } }`. */
    public operator fun String.div(child: String): LayoutDirectory

    /** Nests an already declared directory one level deeper. */
    public operator fun String.div(child: LayoutDirectory): LayoutDirectory

    /** `"gradle" / "libs.versions.toml".file()` is `"gradle" { "libs.versions.toml".file() }`. */
    public operator fun String.div(child: LayoutFile): LayoutFile

    /** Continues a `/` chain with one more directory level. */
    public operator fun LayoutDirectory.div(child: String): LayoutDirectory

    /** Continues a `/` chain with a directory block. */
    public operator fun LayoutDirectory.div(child: LayoutDirectory): LayoutDirectory

    /** Closes a `/` chain with the file it leads to. */
    public operator fun LayoutDirectory.div(child: LayoutFile): LayoutFile
}

/**
 * Receiver of a directory block, `"..." { }`.
 *
 * `ignore()` lives here and nowhere else: it is only ever reachable with a directory of a
 * declared role around it, so "why is this not checked?" always has an answer in the
 * generated documentation. There is deliberately no project-wide `ignore`.
 */
@KatachiDsl
public sealed interface LayoutDirectoryScope : LayoutScope {
    /**
     * One line saying which files belong here, shown when a role may live in more than one
     * place. It answers "which of these should I use?".
     */
    public var description: String?

    /**
     * Allows any file directly inside this directory.
     *
     * Subdirectories are **not** allowed: a file one level further down is still reported.
     * Write it out, because an empty block means the opposite and the two should not look
     * alike.
     */
    public fun anyFile()

    /**
     * Stops the check below this directory, at any depth.
     *
     * Different from [anyFile], which allows the files directly inside and nothing else.
     */
    public fun ignore()
}

/**
 * A directory declared in a `layout { }` block.
 *
 * The value exists so that `/` can nest what was just declared. It is not a path: the
 * declaration is already recorded when the value is handed back.
 */
public class LayoutDirectory internal constructor(
    /** Outermost node of the chain this expression created; the one `/` re-parents. */
    internal val top: LayoutNode,
    /** Innermost node; where the next level attaches. */
    internal val leaf: LayoutNode,
) {
    override fun toString(): String = "LayoutDirectory(${leaf.pathFromDeclaration()})"
}

/** A file declared in a `layout { }` block. */
public class LayoutFile internal constructor(
    internal val top: LayoutNode,
    internal val leaf: LayoutNode,
) {
    /**
     * Stops this file from being reported as missing when it does not exist.
     *
     * A declaration holding a `*` or a `**` is already optional on its own: such a place is
     * one that fills up over time, and zero matches is a normal state for it.
     */
    public fun optional(): LayoutFile {
        leaf.optional = true
        return this
    }

    override fun toString(): String = "LayoutFile(${leaf.pathFromDeclaration()})"
}

/**
 * A `layout { }` block that has been declared but not evaluated.
 *
 * Blocks are deferred on purpose: the same `architecture { }` value is read by tests and
 * (from v0.3) by documentation generation, so building it must not touch the file system
 * or run any check. Evaluating one is what `flattenLayout()` does.
 */
public class LayoutDeclaration internal constructor(
    /** Where `layout { }` was written. */
    public val declaredAt: DeclarationSite,
    /** The block itself. Evaluated by the checker, not by the DSL. */
    @property:InternalKatachiApi
    public val block: LayoutScope.() -> Unit,
) {
    override fun toString(): String = "LayoutDeclaration($declaredAt)"
}
