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
 * A key is read one way only: `"..." { }` is a directory, `"...".module { }` is a Gradle
 * module, and a file is always written with one of the file functions. A key may itself spell
 * out several levels (`"app/ios" { }`), which is the same as nesting one block per level.
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

    /**
     * Declares a Gradle module, by its module path.
     *
     * This is sugar and nothing else. The two blocks below produce exactly the same
     * declarations, and a violation cannot tell which one was written:
     *
     * ```kotlin
     * ":core:domain:common".module {
     *   mainSourceSet / kotlin / "model" / "*".ktFile()
     * }
     *
     * "core/domain/common" {
     *   "build".ignore()
     *   "build.gradle".ktsFile()
     *   mainSourceSet / kotlin / "model" / "*".ktFile()
     * }
     * ```
     *
     * Only those two lines are added. `src/`, `proguard-rules.pro` and the module's own
     * `.gitignore` are not: where sources live is what the role's own layout says, and the
     * other two are neither universal nor required.
     *
     * The module path may hold wildcards, and then the block is evaluated once per module
     * that matches, with [wildcards] holding what that match captured:
     *
     * ```kotlin
     * ":feature:*".module {
     *   mainSourceSet / kotlin / "${wildcards[0].pascalCase}Screen".ktFile()
     * }
     * ```
     *
     * A key with a wildcard stands for the modules that exist, so a key that matches none
     * declares nothing at all and is never reported as missing. A key without one stands for
     * the module it names whether or not it is there, so a module that was deleted shows up
     * as its missing build file rather than silently disappearing from the check.
     *
     * Where a module path lands is [me.tbsten.katachi.check.ModuleResolver]'s answer, which
     * by default replaces `:` with `/`.
     *
     * @throws me.tbsten.katachi.check.GlobSyntaxException when the module path cannot be
     *   read, `":core::data"` or a `**` written anywhere but last.
     * @throws KatachiDeclarationException when written anywhere but directly inside
     *   `layout { }`.
     */
    public fun String.module(block: LayoutDirectoryScope.() -> Unit): LayoutModule

    /**
     * What the module path's wildcards captured, for the module being evaluated.
     *
     * One element per `*`, and one per level for a trailing `**`, so `":feature:**"` against
     * `:feature:hoge:fuga` reads as `["hoge", "fuga"]` and against `:feature` itself as an
     * empty list — take the innermost name with `lastOrNull()`, not `last()`.
     *
     * @throws KatachiDeclarationException when read outside a `module { }` block, where
     *   there is no module path to have captured anything.
     */
    public val wildcards: List<String>

    /**
     * The source set directory `src/<this>`, and nothing more than that.
     *
     * `"commonMain".sourceSet` is `src/commonMain`. `kotlin/` is not implied — write
     * [kotlin] for it — and neither `main` nor `commonMain` is chosen for you, because that
     * would mean guessing at the kind of project this is.
     *
     * ```kotlin
     * val commonMain = "commonMain".sourceSet   // if writing it every time grates
     * ```
     */
    public val String.sourceSet: LayoutDirectory

    /** `src/main`, the same as `"main".sourceSet`. */
    public val mainSourceSet: LayoutDirectory

    /** `src/test`, the same as `"test".sourceSet`. */
    public val testSourceSet: LayoutDirectory

    /**
     * The `kotlin` directory, the same as `"kotlin" { }`.
     *
     * A source set does not imply it, so it is written out on both spellings of a path:
     * `mainSourceSet / kotlin / ...` and `mainSourceSet { kotlin { ... } }`.
     */
    public val kotlin: LayoutDirectory

    /** `"src" / "main"` is the same as `"src" { "main" { } }`. */
    public operator fun String.div(child: String): LayoutDirectory

    /** Nests an already declared directory one level deeper. */
    public operator fun String.div(child: LayoutDirectory): LayoutDirectory

    /** `"gradle" / "libs.versions.toml".file()` is `"gradle" { "libs.versions.toml".file() }`. */
    public operator fun String.div(child: LayoutFile): LayoutFile

    /** Continues a `/` chain with the package directory of the module being evaluated. */
    public operator fun String.div(child: ModulePackage): LayoutDirectory

    /** Continues a `/` chain with one more directory level. */
    public operator fun LayoutDirectory.div(child: String): LayoutDirectory

    /** Continues a `/` chain with a directory block. */
    public operator fun LayoutDirectory.div(child: LayoutDirectory): LayoutDirectory

    /** Closes a `/` chain with the file it leads to. */
    public operator fun LayoutDirectory.div(child: LayoutFile): LayoutFile

    /** `mainSourceSet / kotlin / modulePackage` continues below the module's package. */
    public operator fun LayoutDirectory.div(child: ModulePackage): LayoutDirectory

    /**
     * Opens a block below a directory a `/` chain or a source set just declared.
     *
     * `kotlin { }`, `mainSourceSet { }` and `"commonMain".sourceSet { }` all reach this, and
     * each means the same as writing the directory's name as a key.
     */
    public operator fun LayoutDirectory.invoke(block: LayoutDirectoryScope.() -> Unit): LayoutDirectory

    /** Starts a `/` chain at the module's package directory. */
    public operator fun ModulePackage.div(child: String): LayoutDirectory

    /** Continues below the module's package directory with a directory block. */
    public operator fun ModulePackage.div(child: LayoutDirectory): LayoutDirectory

    /** `modulePackage / "*UseCase".ktFile()` puts the file in the module's package. */
    public operator fun ModulePackage.div(child: LayoutFile): LayoutFile

    /**
     * Opens a block at the module's package directory: `modulePackage { }` is the nested
     * spelling of `modulePackage / ...`.
     */
    public operator fun ModulePackage.invoke(block: LayoutDirectoryScope.() -> Unit): LayoutDirectory
}

/**
 * Receiver of a directory block, `"..." { }`, and of a `module { }` block.
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
