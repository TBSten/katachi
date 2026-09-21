package me.tbsten.katachi.check

import me.tbsten.katachi.dsl.InternalKatachiApi

/**
 * Which files the check considers part of the project.
 *
 * This is not a way to exclude files. `layout { }` is where a directory is declared and, if
 * it should not be looked into, marked `ignore()` — with a role and a `summary` around it,
 * so the reason ends up in the generated documentation. This setting answers the question
 * before that one: which files are the project's at all.
 *
 * The type is closed and the two strategies are the only values, so a project cannot grow a
 * pile of ad-hoc predicates here.
 *
 * ```kotlin
 * val projectArchitecture = architecture {
 *   files = gitTracked()      // the default; leaving it out means the same thing
 *   // files = wholeTree()    // walk the directory tree, ignoring git
 *   domainRoles()
 * }
 * ```
 */
public sealed interface FileSelection {
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
     * like [WholeTree]. When git *does* claim the root but then fails, the check fails instead
     * of quietly walking everything, because that would give different results on a
     * developer's machine and on CI.
     */
    public object GitTracked : FileSelection

    /** Every file in the tree below the project root, whatever git thinks of it. */
    public object WholeTree : FileSelection
}

/**
 * Applies this selection to [delegate], the real tree below [projectRoot].
 *
 * @throws GitUnavailableException for [FileSelection.GitTracked] when git claims the root
 *   but then cannot be run or fails.
 */
@InternalKatachiApi
public fun FileSelection.fileSystemFor(
    delegate: KatachiFileSystem,
    projectRoot: ProjectRoot,
): KatachiFileSystem = when (this) {
    FileSelection.WholeTree -> delegate
    // Not `projectRoot.isGitRepository`: that only sees a `.git` sitting directly at the
    // root, so a project below the repository root would silently fall back to walking the
    // whole tree — and then `build/` and `.DS_Store` start showing up as violations.
    FileSelection.GitTracked ->
        if (isInsideGitWorkTree(projectRoot.path)) gitTrackedFileSystem(delegate, projectRoot.path) else delegate
}
