package me.tbsten.katachi.report.internal

import me.tbsten.katachi.dsl.files.FsPath
import me.tbsten.katachi.dsl.files.internal.runGit

/**
 * Where [root] sits inside its git repository, as `git rev-parse --show-prefix` prints it:
 * `android/` with a trailing `/` for a project one level down, `` for the repository root.
 *
 * `null` when git is missing, [root] is not inside a work tree, the command fails or does not
 * finish in time. The caller then writes paths relative to the project root, which GitHub still
 * resolves through `originalUriBaseIds`. Never throws: the report is a side effect of the
 * check, and git not being there is not the check's problem.
 */
internal fun gitShowPrefix(root: FsPath): String? {
    val output = runGit(root, "rev-parse", "--show-prefix") ?: return null
    // git always answers with `/`, Windows included, which is what a URI needs.
    return output.lineSequence().firstOrNull()?.trim() ?: ""
}
