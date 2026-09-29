package me.tbsten.katachi.dsl

import org.intellij.lang.annotations.Language

/**
 * Receiver of `layout { }`, and of every directory block inside it.
 *
 * Directly under `layout { }` the paths are relative to the project root, so a file that
 * belongs to no module is written there as it is.
 *
 * A key is read one way only: `"..." { }` is a directory, and a file is always written with
 * one of the file functions. A key may itself spell out several levels (`"app/ios" { }`),
 * which is the same as nesting one block per level.
 *
 * [LayoutDirectoryScope] adds the three things that only make sense inside a directory:
 * `description`, `anyFile()` and `ignore()`.
 *
 * ## The vocabulary this interface does not hold
 *
 * What is declared here is the whole of the core: a directory, a file, `/`, stopping the
 * check, and a named wildcard (`capture("...")`). Everything else katachi offers —
 * `"...".ktFile()`, and the Gradle vocabulary (`"...".module { }`, `mainSourceSet`, `kotlin`,
 * `modulePackage`, `wildcards`, `wildcard("...")`) — is written
 * *on top of* it, as functions that take this scope as a context parameter:
 *
 * ```kt
 * context(layoutScope: LayoutScope)
 * public fun String.ktFile(): LayoutFile {
 *   val name = this
 *   return with(layoutScope) { "$name.kt".file() }
 * }
 * ```
 *
 * A member extension cannot be added to an interface from the outside, so anything declared
 * here would be katachi's to write and nobody else's. Declared as above, katachi's own
 * utilities and a project's are the same kind of thing and read the same way at the call
 * site. A project that wants `"...".protoFile()` or `featureSources()` writes it exactly
 * like the function above, in its own package.
 *
 * The Gradle vocabulary lives in `me.tbsten.katachi.dsl.gradle` and is imported as
 * `import me.tbsten.katachi.dsl.gradle.*`.
 *
 * ## What a layout says beyond where files live
 *
 * [FileConstraintScope] comes with this one, so `fileConstraint { }` can be written in any block of a
 * `layout { }`. What it covers is the block it was written in: the entries of that subtree and
 * nothing else. See [FileConstraintScope.fileConstraint].
 *
 * ## Example 1: Declaring a file directly under the project root, and a nested one
 * ```kt
 * layout {
 *   ".gitignore".file()                        // <root>/.gitignore
 *   "gradle" { "libs.versions.toml".file() }   // <root>/gradle/libs.versions.toml
 *   "src" / "main" / "kotlin" / "*.kt".file()  // the same thing, nested with `/`
 * }
 * ```
 */
@KatachiDsl
public sealed interface LayoutScope : FileConstraintScope {
    /**
     * Declares a directory. Nest the blocks to walk down the tree.
     *
     * The key may hold more than one level: `"app/ios" { }` means the same as
     * `"app" { "ios" { } }`.
     *
     * An empty block means *nothing may live here*. Use `anyFile()` to allow anything
     * directly inside, and `ignore()` to stop checking below it altogether.
     *
     * ## Example 1: Declaring a multi-level directory and ignoring it
     * ```kt
     * layout {
     *   "app/ios" { ignore() }
     * }
     * ```
     */
    public operator fun String.invoke(block: LayoutDirectoryScope.() -> Unit): LayoutDirectory

    /**
     * Declares a file, named exactly as written: `"libs.versions.toml".file()`.
     *
     * ## Example 1: Declaring a file at the project root
     * ```kt
     * layout {
     *   "libs.versions.toml".file()
     * }
     * ```
     */
    public fun String.file(): LayoutFile

    /**
     * Declares a directory and stops the check below it, at any depth.
     *
     * `"build".ignore()` is the short form of `"build" { ignore() }`; the two produce the
     * same declaration.
     *
     * ## Example 1: Stopping the check below a build output directory
     * ```kt
     * layout {
     *   "build".ignore()
     * }
     * ```
     */
    public fun String.ignore(): LayoutDirectory

    /**
     * `"src" / "main"` is the same as `"src" { "main" { } }`.
     *
     * ## Example 1: Chaining two directory levels
     * ```kt
     * layout {
     *   "app" / "ios"
     * }
     * ```
     */
    public operator fun String.div(child: String): LayoutDirectory

    /**
     * Nests an already declared directory one level deeper.
     *
     * ## Example 1: Putting an already declared directory one level deeper
     * ```kt
     * layout {
     *   "x" / "app/ios" { ignore() }
     * }
     * ```
     */
    public operator fun String.div(child: LayoutDirectory): LayoutDirectory

    /**
     * `"gradle" / "libs.versions.toml".file()` is `"gradle" { "libs.versions.toml".file() }`.
     *
     * ## Example 1: Closing a `/` chain with an optional file
     * ```kt
     * layout {
     *   "gradle" / "libs.versions.toml".file().optional()
     * }
     * ```
     */
    public operator fun String.div(child: LayoutFile): LayoutFile

    /**
     * Continues a `/` chain with one more directory level.
     *
     * ## Example 1: Continuing a chain that started from a shared directory value
     * ```kt
     * layout {
     *   mainSourceSet / "proto"
     * }
     * ```
     */
    public operator fun LayoutDirectory.div(child: String): LayoutDirectory

    /**
     * Continues a `/` chain with a directory block.
     *
     * ## Example 1: Chaining two directory values together
     * ```kt
     * layout {
     *   mainSourceSet / kotlin / "Foo".ktFile()
     * }
     * ```
     */
    public operator fun LayoutDirectory.div(child: LayoutDirectory): LayoutDirectory

    /**
     * Closes a `/` chain with the file it leads to.
     *
     * ## Example 1: Closing a chain with the file it leads to
     * ```kt
     * layout {
     *   mainSourceSet / "Foo".ktFile()
     * }
     * ```
     */
    public operator fun LayoutDirectory.div(child: LayoutFile): LayoutFile

    /**
     * Opens a block below a directory a `/` chain or a source set just declared.
     *
     * `kotlin { }`, `mainSourceSet { }` and `"commonMain".sourceSet { }` all reach this, and
     * each means the same as writing the directory's name as a key.
     *
     * ## Example 1: Opening a block below a source set value
     * ```kt
     * layout {
     *   mainSourceSet { "Foo".ktFile() }
     * }
     * ```
     */
    public operator fun LayoutDirectory.invoke(block: LayoutDirectoryScope.() -> Unit): LayoutDirectory

    /**
     * Names a wildcard: a marker `capture("feature")` embeds in a layout key, read back out
     * wherever the key is used and replaced with a plain `*` before the check ever sees it.
     *
     * Substituting `"*"` for every `capture(...)` in a definition changes nothing about what is
     * checked or about the flattened layout -- the name is only what a template fills in,
     * generating a file for the role from the level's value at `--arg feature=...`. Because it
     * is an ordinary `String`, it can name a whole level (`capture("feature")`) or part of one:
     * `"${capture("fileName")}Screen".ktFile()` and `"feature-${capture("x")}"` both work, with
     * no overload of their own.
     *
     * The same name may appear once along one path; a different path of the same role may reuse
     * it, and then the two share the one parameter. Two captures may not sit directly next to
     * each other with nothing between them -- see [KatachiAdjacentCaptureException] -- and a
     * name written where no layout key ever reads it is refused too, see
     * [KatachiStrayCaptureTokenException].
     *
     * ## Example 1: Naming a whole level, and part of one
     * ```kt
     * layout {
     *   "feature" / capture("feature") / "src" / "main" / "kotlin" / "${capture("fileName")}ViewModel.kt".file()
     * }
     * ```
     *
     * @param name the parameter name, following the same rule as a role name
     *   (`[A-Za-z][A-Za-z0-9_-]*`).
     * @throws KatachiInvalidIdentifierException when [name] is not an identifier.
     * @throws KatachiDuplicateCaptureException when [name] is already used along the same path,
     *   noticed when the layout is flattened.
     * @throws KatachiAdjacentCaptureException when this token ends up touching another wildcard
     *   with nothing literal between them, noticed when the layout is flattened.
     */
    public fun capture(name: String): String

    /**
     * [capture] opened as a block: `capture("feature") { ... }` is
     * `capture("feature").invoke { ... }`, the same `String.invoke` a whole-level key opens with.
     *
     * A separate overload because a trailing lambda right after `capture("...")` is read as an
     * argument of the call, not as an `invoke` on what it returns.
     *
     * ## Example 1: Declaring several files below a named level
     * ```kt
     * layout {
     *   "feature" {
     *     capture("feature") {
     *       "build.gradle.kts".file()
     *       "src" / "main" / "kotlin" / "*ViewModel".ktFile()
     *     }
     *   }
     * }
     * ```
     */
    public fun capture(name: String, block: LayoutDirectoryScope.() -> Unit): LayoutDirectory
}

/**
 * Receiver of a directory block, `"..." { }`, and of a `module { }` block.
 *
 * `ignore()` lives here and nowhere else: it is only ever reachable with a directory of a
 * declared role around it, so "why is this not checked?" always has an answer in the
 * generated documentation. There is deliberately no project-wide `ignore`.
 *
 * ## Example 1: Allowing any file directly inside a directory, plus one named file
 * ```kt
 * layout {
 *   "generated" {
 *     anyFile()
 *     "README.md".file()
 *   }
 * }
 * ```
 */
@KatachiDsl
public sealed interface LayoutDirectoryScope : LayoutScope {
    /**
     * One line saying which files belong here, shown when a role may live in more than one
     * place. It answers "which of these should I use?".
     *
     * ## Example 1: Documenting why a directory exists
     * ```kt
     * layout {
     *   "core/domain" {
     *     description = "Shared by more than one feature"
     *     "*UseCase".ktFile()
     *   }
     * }
     * ```
     */
    @get:Language("markdown")
    @set:Language("markdown")
    public var description: String?

    /**
     * Allows any file directly inside this directory.
     *
     * Subdirectories are **not** allowed: a file one level further down is still reported.
     * Write it out, because an empty block means the opposite and the two should not look
     * alike.
     *
     * ## Example 1: Allowing any file directly inside a generated-code directory
     * ```kt
     * layout {
     *   "generated" { anyFile() }
     * }
     * ```
     */
    public fun anyFile()

    /**
     * Stops the check below this directory, at any depth.
     *
     * Different from [anyFile], which allows the files directly inside and nothing else.
     *
     * ## Example 1: Stopping the check below a build output directory
     * ```kt
     * layout {
     *   "build" { ignore() }
     * }
     * ```
     */
    public fun ignore()
}
