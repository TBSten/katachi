package me.tbsten.katachi.dsl

import me.tbsten.katachi.ExperimentalKatachiApi
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
 * This interface holds the core only: a directory, a file, `/`, stopping the check, and a
 * named wildcard (`capture("...")`). Everything else, such as `"...".ktFile()` and the Gradle
 * vocabulary (`mainSourceSet`, `kotlin`, `"...".sourceSet`), is a function that takes this scope
 * as a context parameter:
 *
 * ```kt
 * context(layoutScope: LayoutScope)
 * public fun String.ktFile(): LayoutFile {
 *   val name = this
 *   return with(layoutScope) { "$name.kt".file() }
 * }
 * ```
 *
 * A project that wants its own `"...".protoFile()` or `featureSources()` writes it the same
 * way, in its own package, and it reads like katachi's own at the call site.
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
 *   "src" / "main" / "kotlin" / "*.kt".file()  // levels joined with `/`
 *   "build".ignore()                           // stop checking below build/
 *   mainSourceSet / kotlin / "*.kt".file()     // a chain may start from a directory value
 *   mainSourceSet { "Foo".ktFile() }           // and a directory value may open a block
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
     */
    public operator fun String.invoke(block: LayoutDirectoryScope.() -> Unit): LayoutDirectory

    /**
     * Declares a file, named exactly as written: `"libs.versions.toml".file()`.
     */
    public fun String.file(): LayoutFile

    /**
     * Declares a directory and stops the check below it, at any depth.
     *
     * `"build".ignore()` is the short form of `"build" { ignore() }`; the two produce the
     * same declaration.
     */
    public fun String.ignore(): LayoutDirectory

    /**
     * `"src" / "main"` is the same as `"src" { "main" { } }`.
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
     */
    public operator fun String.div(child: LayoutFile): LayoutFile

    /**
     * Continues a `/` chain with one more directory level.
     */
    public operator fun LayoutDirectory.div(child: String): LayoutDirectory

    /**
     * Continues a `/` chain with a directory block.
     */
    public operator fun LayoutDirectory.div(child: LayoutDirectory): LayoutDirectory

    /**
     * Closes a `/` chain with the file it leads to.
     */
    public operator fun LayoutDirectory.div(child: LayoutFile): LayoutFile

    /**
     * Opens a block below a directory a `/` chain or a source set just declared.
     *
     * `kotlin { }`, `mainSourceSet { }` and `"commonMain".sourceSet { }` all reach this, and
     * each means the same as writing the directory's name as a key.
     */
    public operator fun LayoutDirectory.invoke(block: LayoutDirectoryScope.() -> Unit): LayoutDirectory

    /**
     * Names a wildcard in a layout key, so a template can fill that level from `--arg feature=...`.
     *
     * For the check, a capture written once along a path matches like a plain `*`. It is an
     * ordinary `String`, so it can name a whole level (`capture("feature")`) or part of one:
     * `"${capture("fileName")}Screen".ktFile()` and `"feature-${capture("x")}"` both work.
     *
     * The same name written twice along one path is the declaration that the two levels read the
     * same value: the check then allows only a file whose levels agree, and reports the others as
     * `[CaptureMismatch]`; a template fills both from the one `--arg`. A naming conversion on the
     * token -- `"${capture("feature").pascalCase}Screen"` -- derives that level from the plain
     * `capture("feature")` of the same path in the same way (see [nameWords]). A different path of
     * the same role may reuse a name freely, and then the two paths only share the one parameter.
     * Two captures may not sit directly next to each other with nothing between them -- see
     * [KatachiAdjacentCaptureException] -- nor inside `{...}` or `[...]`, see
     * [KatachiCaptureInsideBracketsException] -- and a name written where no layout key ever
     * reads it is refused too, see [KatachiStrayCaptureTokenException]. A capture that shares its
     * level with other characters may take the empty string: `"${capture("name")}Screen"` also
     * matches `Screen.kt`, with `name` empty.
     *
     * ## Example 1: Naming a whole level, part of one, and tying two levels together
     * ```kt
     * layout {
     *   "feature" / capture("feature") / "src" / "main" / "kotlin" / "${capture("fileName")}ViewModel.kt".file()
     *   // feature/home/.../home/HomeScreen.kt is allowed; feature/home/.../settings/HomeScreen.kt is not
     *   "feature" / capture("feature") / "src" / "main" / "kotlin" / capture("feature") / "${capture("feature").pascalCase}Screen.kt".file()
     * }
     * ```
     *
     * @param name the parameter name, following the same rule as a role name
     *   (`[A-Za-z][A-Za-z0-9_-]*`).
     * @throws KatachiInvalidIdentifierException when [name] is not an identifier.
     * @throws KatachiDerivedCaptureWithoutSourceException when a conversion is applied to a name
     *   that is not also written plain on the same path, noticed when the layout is flattened.
     * @throws KatachiAdjacentCaptureException when this token ends up touching another wildcard
     *   with nothing literal between them, noticed when the layout is flattened.
     */
    @ExperimentalKatachiApi
    public fun capture(name: String): String

    /**
     * [capture] opened as a block: `capture("feature") { ... }` declares a named directory level.
     *
     * It is the same as `capture("feature").invoke { ... }`; a separate overload because a trailing lambda right after `capture("...")` is read as an
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
    @ExperimentalKatachiApi
    public fun capture(name: String, block: LayoutDirectoryScope.() -> Unit): LayoutDirectory
}

/**
 * Receiver of a directory block, `"..." { }`.
 *
 * `ignore()` lives here and nowhere else: it is only ever reachable with a directory of a
 * declared role around it, so "why is this not checked?" always has an answer in the
 * generated documentation. There is deliberately no project-wide `ignore`.
 *
 * ## Example 1: Allowing any file directly inside a directory, plus one named file
 * ```kt
 * layout {
 *   "generated" {
 *     description = "Code written by the build; do not edit by hand"
 *     anyFile()
 *     "README.md".file()
 *   }
 *   "build" { ignore() }
 * }
 * ```
 */
@KatachiDsl
public sealed interface LayoutDirectoryScope : LayoutScope {
    /**
     * One line saying which files belong here, shown when a role may live in more than one
     * place. It answers "which of these should I use?".
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
     */
    public fun anyFile()

    /**
     * Stops the check below this directory, at any depth.
     *
     * Different from [anyFile], which allows the files directly inside and nothing else.
     */
    public fun ignore()
}
