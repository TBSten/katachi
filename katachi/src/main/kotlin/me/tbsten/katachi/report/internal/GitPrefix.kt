package me.tbsten.katachi.report.internal

import me.tbsten.katachi.dsl.files.FsPath
import me.tbsten.katachi.dsl.files.internal.runGit

// null when git is missing, root is outside a work tree, or the command fails or times out; never throws, the
// report must not fail the check.
internal fun gitShowPrefix(root: FsPath): String? {
    val output = runGit(root, "rev-parse", "--show-prefix") ?: return null
    // git always answers with `/`, Windows included, which is what a URI needs.
    return output.lineSequence().firstOrNull()?.trim() ?: ""
}
