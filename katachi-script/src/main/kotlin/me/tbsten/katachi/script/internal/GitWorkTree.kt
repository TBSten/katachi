package me.tbsten.katachi.script.internal

import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText

/** The top of the git work tree that holds [path]: [path] or the nearest directory above it with a `.git` entry. */
internal fun gitWorkTreeOf(path: Path): Path? =
    generateSequence(path.toAbsolutePath().normalize()) { it.parent }.firstOrNull { it.resolve(".git").exists() }

/**
 * The repository [workTree] belongs to: its `.git` directory, or, for a linked worktree whose `.git` is a
 * `gitdir: <repository>/.git/worktrees/<name>` file, the directory that `commondir` there points to.
 */
internal fun gitCommonDirOf(workTree: Path): Path? {
    val dotGit = workTree.resolve(".git")
    if (dotGit.isDirectory()) return dotGit.realOrSelf()
    if (!dotGit.isRegularFile()) return null
    val gitdir = dotGit.readText().lineSequence()
        .map { it.trim() }
        .firstOrNull { it.startsWith("gitdir:") }
        ?.removePrefix("gitdir:")?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: return null
    val dir = workTree.resolve(gitdir).normalize()
    val common = dir.resolve("commondir").takeIf { it.isRegularFile() }?.readText()?.trim()
    return (if (common.isNullOrEmpty()) dir else dir.resolve(common).normalize()).realOrSelf()
}

/**
 * [recorded] moved into the work tree of [here], when [here] is in another work tree of the same
 * repository (`git worktree add`); null when they share a work tree or belong to different repositories.
 */
internal fun sameRepositoryCounterpart(recorded: Path, here: Path): Path? {
    val recordedPath = recorded.toAbsolutePath().normalize()
    val there = gitWorkTreeOf(recordedPath) ?: return null
    val current = gitWorkTreeOf(here) ?: return null
    if (there.realOrSelf() == current.realOrSelf()) return null
    val repository = gitCommonDirOf(there) ?: return null
    if (repository != gitCommonDirOf(current)) return null
    return current.resolve(there.relativize(recordedPath)).normalize()
}

private fun Path.realOrSelf(): Path = runCatching { toRealPath() }.getOrDefault(this)
