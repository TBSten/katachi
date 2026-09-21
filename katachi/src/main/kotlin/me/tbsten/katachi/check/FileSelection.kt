package me.tbsten.katachi.check

import me.tbsten.katachi.InternalKatachiApi

/**
 * Which files the check considers part of the project.
 *
 * This is not a way to exclude files. `layout { }` is where a directory is declared and, if
 * it should not be looked into, marked `ignore()` — with a role and a `summary` around it,
 * so the reason ends up in the generated documentation. This setting answers the question
 * before that one: which files are the project's at all.
 *
 * [GitTracked] and [WholeTree] are the two katachi ships, and they are ordinary
 * implementations of this interface rather than a closed set. A project whose sources are
 * listed by something other than git — a Bazel workspace, a generated manifest, an in-house
 * tool — writes its own and assigns it to `files` the same way; [fileSystemFor] is the one
 * thing it has to answer.
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
 *     "domain".group { "UseCase" { } }
 * }
 * ```
 *
 * ## Example 3: select the files Bazel manages, with a selection of your own
 * ```kt
 * import me.tbsten.katachi.check.FileSelection
 * import me.tbsten.katachi.check.KatachiFileSystem
 * import me.tbsten.katachi.check.ProjectRoot
 * import me.tbsten.katachi.InternalKatachiApi
 * import me.tbsten.katachi.dsl.architecture
 *
 * // Only what `bazel query` reports as a source of this workspace. BazelSourceFileSystem is
 * // the project's own KatachiFileSystem: it wraps the delegate and hides everything else.
 * @OptIn(InternalKatachiApi::class)
 * object BazelSources : FileSelection {
 *     override fun fileSystemFor(
 *         delegate: KatachiFileSystem,
 *         projectRoot: ProjectRoot,
 *     ): KatachiFileSystem = BazelSourceFileSystem(delegate, projectRoot.path)
 * }
 *
 * val projectArchitecture = architecture {
 *     files = BazelSources
 *     "domain".group { "UseCase" { } }
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
     * Marked [InternalKatachiApi] because [KatachiFileSystem] and [ProjectRoot] are.
     * Implementing this means reaching into how the check runs, and that surface can change
     * between releases — which is worth saying out loud, not worth forbidding.
     *
     * ## Example 1: pass the tree through untouched
     * ```kt
     * import me.tbsten.katachi.check.FileSelection
     * import me.tbsten.katachi.check.KatachiFileSystem
     * import me.tbsten.katachi.check.ProjectRoot
     * import me.tbsten.katachi.InternalKatachiApi
     *
     * @OptIn(InternalKatachiApi::class)
     * object Everything : FileSelection {
     *     override fun fileSystemFor(
     *         delegate: KatachiFileSystem,
     *         projectRoot: ProjectRoot,
     *     ): KatachiFileSystem = delegate
     * }
     * ```
     */
    @InternalKatachiApi
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
     * Whether git applies is decided by asking git, not by looking for a `.git` next to the
     * project root. A Gradle project often sits below the repository root — a sample inside
     * the library's own repository, a build in a monorepo, a submodule — and there
     * `.git` is somewhere above while `git ls-files` run at the project root still answers
     * correctly, with paths relative to that directory.
     *
     * When git says the root is not inside a work tree, or git is not installed, this behaves
     * like [WholeTree]. When git *does* claim the root but then fails, the check fails with
     * [KatachiGitUnavailableException] instead of quietly walking everything, because that would
     * give different results on a developer's machine and on CI.
     *
     * ## Example 1: select it explicitly
     * ```kt
     * val projectArchitecture = architecture {
     *     files = gitTracked()
     * }
     * ```
     */
    public object GitTracked : FileSelection {
        @InternalKatachiApi
        override fun fileSystemFor(
            delegate: KatachiFileSystem,
            projectRoot: ProjectRoot,
        ): KatachiFileSystem =
            // Not `projectRoot.isGitRepository`: that only sees a `.git` sitting directly at the
            // root, so a project below the repository root would silently fall back to walking the
            // whole tree — and then `build/` and `.DS_Store` start showing up as violations.
            if (isInsideGitWorkTree(projectRoot.path)) gitTrackedFileSystem(delegate, projectRoot.path) else delegate
    }

    /**
     * Every file in the tree below the project root, whatever git thinks of it.
     *
     * ## Example 1: ignore git and walk the whole tree
     * ```kt
     * val projectArchitecture = architecture {
     *     files = wholeTree()
     *     "domain".group { "UseCase" { } }
     * }
     * ```
     */
    public object WholeTree : FileSelection {
        @InternalKatachiApi
        override fun fileSystemFor(
            delegate: KatachiFileSystem,
            projectRoot: ProjectRoot,
        ): KatachiFileSystem = delegate
    }
}
