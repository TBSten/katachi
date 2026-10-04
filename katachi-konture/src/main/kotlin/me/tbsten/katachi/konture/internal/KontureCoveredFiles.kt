package me.tbsten.katachi.konture.internal

import java.io.File

/**
 * The files one constraint covers, indexed so that a path Konture reports can be turned back
 * into the project relative spelling the report wants.
 *
 * Konture reports a violation under the path its parse cache remembers for the file, which is
 * the spelling of the first parse in this JVM and not necessarily the one katachi asked for
 * (`/tmp` against `/private/tmp`, `src/../src/`, a symlinked worktree). The cheap answer is
 * tried first: strip the project root and look the rest up. Only when that fails are both
 * sides canonicalised, and the canonical map is built the first time it is needed, so a run
 * whose spellings agree never touches the file system here.
 */
internal class KontureCoveredFiles(
    /** Absolute, `/` separated, as `FileConstraintSubject.projectRoot` spells it. */
    private val projectRoot: String,
    /** Project relative, `/` separated, as `FileConstraintSubject.files` spells them. */
    files: Collection<String>,
) {
    private val relative: Set<String> = files.toHashSet()

    private val relativeByCanonicalPath: Map<String, String> by lazy {
        relative.associateBy { File(projectRoot, it).canonicalPath }
    }

    /**
     * The project relative spelling of [absolutePath] when it is one of the covered files, in
     * either spelling, or `null` when it is not covered at all.
     */
    fun relativeOf(absolutePath: String): String? {
        val reported = File(absolutePath)
        val direct = reported.invariantSeparatorsPath.removePrefix("$projectRoot/")
        if (direct in relative) return direct
        return relativeByCanonicalPath[reported.canonicalPath]
    }
}
