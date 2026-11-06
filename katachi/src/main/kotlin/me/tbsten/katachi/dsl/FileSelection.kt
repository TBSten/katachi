package me.tbsten.katachi.dsl

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.files.KatachiFileSystem
import me.tbsten.katachi.dsl.files.ProjectRoot
import me.tbsten.katachi.dsl.files.internal.gitTrackedFileSystem
import me.tbsten.katachi.dsl.files.internal.isInsideGitWorkTree

/**
 * Which files the check considers part of the project.
 *
 * This is not a way to exclude files: mark a directory `ignore()` in `layout { }` for that, so
 * the reason ends up in the generated documentation. This setting decides which files are the
 * project's at all.
 *
 * katachi ships [GitTracked] (the default) and [WholeTree]. A project whose sources are listed by
 * something else (a Bazel workspace, a generated manifest) implements [fileSystemFor] and assigns
 * its own selection to `files`.
 *
 * ## Why a selection of your own is allowed at all
 *
 * A selection can hide any path, so it is the one place a file can be left out of the check
 * without a role, and without the reason ending up in the generated documentation. katachi
 * accepts that opening because "which files are the project's" does not always have git for an
 * answer: a Bazel workspace or a generated manifest is the only honest source of that list. Keep
 * a selection to that one question. A path left out for any other reason belongs in `ignore()`,
 * where the reason is recorded next to it.
 *
 * ## Example 1: keep the default (gitTracked())
 * ```kt
 * val projectArchitecture = architecture {
 *     files = gitTracked() // the default; can be left out
 * }
 * ```
 *
 * ## Example 2: switch to wholeTree()
 * ```kt
 * val projectArchitecture = architecture {
 *     files = wholeTree()
 *     "domain".group { "UseCase".role { } }
 * }
 * ```
 *
 * ## Example 3: select the files Bazel manages, with a selection of your own
 * ```kt
 * import me.tbsten.katachi.dsl.FileSelection
 * import me.tbsten.katachi.dsl.files.KatachiFileSystem
 * import me.tbsten.katachi.dsl.files.ProjectRoot
 * import me.tbsten.katachi.ExperimentalKatachiApi
 * import me.tbsten.katachi.dsl.architecture
 *
 * // Only what `bazel query` reports as a source of this workspace. BazelSourceFileSystem is
 * // the project's own KatachiFileSystem: it wraps the delegate and hides everything else.
 * @OptIn(ExperimentalKatachiApi::class)
 * object BazelSources : FileSelection {
 *     override fun fileSystemFor(
 *         delegate: KatachiFileSystem,
 *         projectRoot: ProjectRoot,
 *     ): KatachiFileSystem = BazelSourceFileSystem(delegate, projectRoot.path)
 * }
 *
 * val projectArchitecture = architecture {
 *     files = BazelSources
 *     "domain".group { "UseCase".role { } }
 * }
 * ```
 */
public interface FileSelection {
    /**
     * Applies this selection to [delegate], the real tree below [projectRoot], and returns
     * the view the check then walks.
     *
     * Returning [delegate] unchanged selects every file below the root, which is all
     * [WholeTree] does. A selection that leaves files out returns a view that hides them,
     * and it has to hide a directory together with everything below it: a directory that
     * still lists but answers nothing is reported as unexpected.
     *
     * Marked [ExperimentalKatachiApi] because [KatachiFileSystem] and [ProjectRoot] are.
     */
    @ExperimentalKatachiApi
    public fun fileSystemFor(
        delegate: KatachiFileSystem,
        projectRoot: ProjectRoot,
    ): KatachiFileSystem

    /**
     * Everything `git ls-files --cached --others --exclude-standard` reports: what git
     * tracks, plus what is not tracked yet but not ignored either.
     *
     * A file git does not manage cannot be given a role. `.DS_Store` turns up in every
     * directory, `local.properties` is per machine, `build/` is generated output.
     *
     * A file tracked below an ignored directory (one added with `git add -f`, say) is checked
     * like any other. A project below the repository root (a monorepo, a submodule) works too.
     *
     * When the root is not inside a git work tree, or git is not installed, this behaves like
     * [WholeTree]. When git claims the root but then fails, the check fails with
     * [KatachiGitUnavailableException] rather than giving different results on CI.
     *
     * ## Example 1: select it explicitly
     * ```kt
     * val projectArchitecture = architecture {
     *     files = gitTracked()
     * }
     * ```
     */
    public object GitTracked : FileSelection {
        @ExperimentalKatachiApi
        override fun fileSystemFor(
            delegate: KatachiFileSystem,
            projectRoot: ProjectRoot,
        ): KatachiFileSystem =
            // Asked of git, not read off a `.git` at the root: a project below the repository
            // root would otherwise silently fall back to walking the whole tree — and then
            // `build/` and `.DS_Store` start showing up as violations.
            if (isInsideGitWorkTree(projectRoot.path)) gitTrackedFileSystem(delegate, projectRoot.path) else delegate
    }

    /**
     * Every file in the tree below the project root, whatever git thinks of it.
     *
     * `build/`, `.gradle/` and `.idea/` are directories like any other here: declare them or
     * `ignore()` them. Only `.git` is never walked.
     *
     * ## Example 1: ignore git and walk the whole tree
     * ```kt
     * val projectArchitecture = architecture {
     *     files = wholeTree()
     *     "BuildOutput".role { layout { "build".ignore(); ".gradle".ignore() } }
     *     "domain".group { "UseCase".role { } }
     * }
     * ```
     */
    public object WholeTree : FileSelection {
        @ExperimentalKatachiApi
        override fun fileSystemFor(
            delegate: KatachiFileSystem,
            projectRoot: ProjectRoot,
        ): KatachiFileSystem = delegate
    }
}
