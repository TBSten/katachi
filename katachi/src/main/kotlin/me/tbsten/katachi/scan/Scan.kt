package me.tbsten.katachi.scan

import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.DeclaredConstraint
import me.tbsten.katachi.dsl.LayoutEntry
import me.tbsten.katachi.dsl.Role
import me.tbsten.katachi.dsl.evaluateLayout
import me.tbsten.katachi.fs.FsPath
import me.tbsten.katachi.fs.KatachiFileSystem
import me.tbsten.katachi.fs.findProjectRoot

/**
 * Directories that are not this project's files at all, whatever `files` says.
 *
 * The list stops here. `.kotlin/`, `local.properties`, `.DS_Store` and `build/` are **not**
 * on it: what a machine leaves lying around never converges into a list, which is exactly
 * the job the default `files = gitTracked()` hands to git. Under that default this set is
 * already redundant; it earns its place under `wholeTree()`, and when a project root was
 * found without git being there to answer.
 */
private val FOREIGN_DIRECTORY_NAMES: Set<String> = setOf(".git", ".gradle", ".idea")

/**
 * What the project root is called when it is the project root itself that could not be read.
 *
 * Its path relative to itself is the empty string, which would print as a first line with
 * nothing after the label. `internal` rather than private so that the module search, which
 * names directories the same way and can fail at the root too, spells it the same way by
 * construction.
 */
internal const val ROOT_PATH: String = "."

/**
 * Everything one walk of the tree produced.
 *
 * The walk answers two questions at once, and it has to: which files nobody declared, and
 * which role each file that was declared belongs to. They are the same traversal seen from
 * either side, so running it twice would be the same work done twice and, worse, two chances
 * for the two answers to stop agreeing.
 */
internal class ScanResult(
    /** Every violation of the run, already ordered by block kind. */
    val violations: List<Violation>,
    /**
     * Files that exist, per role that allows them, in walk order. A file two roles both allow
     * is listed under both; a role whose declarations matched nothing is absent rather than
     * present with an empty list, which is why reads go through `orEmpty()`.
     */
    val filesByRole: Map<Role, List<String>>,
    /**
     * The files more than one role turned out to claim, grouped by the roles claiming them.
     *
     * Collected by the same walk rather than derived from [filesByRole] afterwards, for two
     * reasons. [filesByRole] answers "does some role allow this file", so it holds the entries
     * a `module { }` key injects as well, and a `build.gradle.kts` every role of a module
     * carries is not an overlap anyone wrote. And a group has to name the lines that declared
     * it, which is an entry, not a role.
     */
    val fileOverlaps: List<FileOverlap>,
    /**
     * The constraints the layout declared, with the layout around each already evaluated.
     *
     * None of them has been run. They ride on the walk's result rather than being evaluated
     * separately so that a check asks about the same entries this walk matched files against:
     * flattening the layout a second time would be a second chance for the two to disagree
     * about what a wildcard module key expanded to.
     */
    val constraints: List<DeclaredConstraint>,
    /**
     * Where the walk started, absolute — the one value a constraint backend needs that is not
     * project relative, because a parser cannot be pointed at a relative path.
     */
    val projectRoot: FsPath,
)

/**
 * The one walk everything is built on, reached through
 * [me.tbsten.katachi.processor.ProjectModel] — which runs it at most once, so that a run
 * answering both "what is wrong" and "what does this role own" walks the tree once.
 *
 * Finding the root, selecting the files and evaluating the definition are not caught. Each
 * produces the one thing the walk needs, so a failure in any of them leaves nothing to check
 * and nothing to report — there is no "the rest of the run" to save.
 *
 * **The search for the modules is the exception, and it is caught per directory.** That
 * reasoning does not reach it: a directory whose listing fails costs the modules below that
 * one directory, while every sibling subtree still answers which modules it holds. So the
 * search hands back what it found together with the directories it could not read, and those
 * ride into the report as [UncheckedDirectory] blocks with
 * [UncheckedDirectoryReason.ModulesNotDiscovered] — which is what keeps a wildcard module key
 * quietly expanding to too few modules from looking like a project that simply has too few.
 *
 * The project root is looked for in [fileSystem] itself, unfiltered: a marker such as
 * `.git/HEAD` belongs to none of the file sets `files` can select, so the search has to run
 * before the selection is applied. The module index is built the same way, because a module
 * whose `build.gradle.kts` a file set happens to leave out is still a module, and a module
 * path key with a wildcard would otherwise quietly expand to nothing.
 *
 * @throws me.tbsten.katachi.fs.KatachiProjectRootNotFoundException when no directory above
 *   the working directory carries a Gradle, Maven or git marker.
 * @throws me.tbsten.katachi.fs.KatachiGitUnavailableException when `files = gitTracked()` and
 *   the project root is a git repository, but git cannot be run.
 */
internal fun Architecture.scanProject(fileSystem: KatachiFileSystem): ScanResult {
    val projectRoot = findProjectRoot(fileSystem)
    val modules = scanModules(fileSystem, projectRoot.path)
    // One evaluation of the layout, read from both sides: the entries drive the walk, the
    // constraints ride along to whichever check evaluates them. Flattening twice would be a
    // second chance for the two to disagree about what a wildcard module key expanded to.
    val evaluation = evaluateLayout(modules.indexWith(moduleResolver))
    return Scan(
        fileSystem = files.fileSystemFor(fileSystem, projectRoot),
        root = projectRoot.path,
        layout = LayoutIndex(evaluation.entries),
        constraints = evaluation.constraints,
        moduleFailures = modules.unchecked,
    ).run()
}

/**
 * One walk of the tree below the project root.
 *
 * The walk is driven by the declarations, not by the tree: it descends into a directory only
 * while some role still has something to say about what is inside it, and reports the
 * directory itself otherwise.
 *
 * It also never gives up on the whole run because of one path. Every unit that can answer on
 * its own — a child of a directory, a listing, a required declaration — is wrapped in
 * [catching], and whatever comes out of it becomes an [UncheckedFile] or an
 * [UncheckedDirectory] rather than the end of the check. One run that reports everything is
 * the whole point of `assert()`, and a file that throws is almost never the file the reader
 * came to fix.
 */
private class Scan(
    private val fileSystem: KatachiFileSystem,
    private val root: FsPath,
    private val layout: LayoutIndex,
    private val constraints: List<DeclaredConstraint>,
    /**
     * What the search for the modules could not read, which happened before this walk started
     * and belongs in the same report. See [scanProject].
     */
    private val moduleFailures: List<UncheckedDirectory>,
) {
    private val violations = mutableListOf<Violation>()

    /** Every file the walk reached, as a project relative path. */
    private val visitedFiles = mutableSetOf<String>()

    /** The files each role turned out to own, in walk order. See [ScanResult.filesByRole]. */
    private val filesByRole = LinkedHashMap<Role, MutableList<String>>()

    /** The files more than one role claimed, grouped as they are found. See [FileOverlaps]. */
    private val fileOverlaps = FileOverlaps()

    fun run(): ScanResult {
        // First, because the search ran before the walk did. `sortedBy` below is stable, so
        // within the `Failed` group that order is what the reader sees.
        violations += moduleFailures
        walk(root, "")
        for (entry in layout.requiredFiles) {
            // One declaration at a time: the entries are independent of each other, so the
            // ones after a bad entry still have an answer to give.
            catching { reportIfMissing(entry) }
                .onFailure { cause -> violations += UncheckedFile(path = entry.path, cause = cause) }
        }
        return ScanResult(
            // Blocks are grouped by kind, because the reader does something different with
            // each group. `sortedBy` is stable, so within a group the walk order survives:
            // parents before children, names in order.
            violations = violations.sortedBy { it.kind.ordinal },
            filesByRole = filesByRole,
            fileOverlaps = fileOverlaps.toList(),
            constraints = constraints,
            projectRoot = root,
        )
    }

    private fun reportIfMissing(entry: LayoutEntry) {
        if (entry.path in visitedFiles) return
        violations += MissingFile(
            path = entry.path,
            role = entry.role,
            declaredAt = entry.declaredAt,
        )
    }

    private fun walk(directory: FsPath, path: String) {
        // A listing that fails takes the whole directory with it — there are no children to
        // fall back to — but its parent still has the rest of its own children to walk.
        val children = catching { fileSystem.list(directory) }.getOrElse { cause ->
            violations += UncheckedDirectory(
                path = path.ifEmpty { ROOT_PATH },
                reason = UncheckedDirectoryReason.NotWalked,
                cause = cause,
            )
            return
        }
        for (child in children) {
            visitChild(child, if (path.isEmpty()) child.name else "$path/${child.name}")
        }
    }

    /**
     * One child of a directory, with everything done for it held to that child.
     *
     * This is where the walk is cut into independent pieces: a sibling is another turn of the
     * loop above, and anything deeper is cut up again by the [walk] this ends up calling. So
     * what a failure here actually costs is this one path.
     */
    private fun visitChild(child: FsPath, path: String) {
        val isDirectory = catching { fileSystem.isDirectory(child) }.getOrElse { cause ->
            // Which of the two it is is exactly what could not be answered, and
            // [UncheckedFile] is the smaller claim: an [UncheckedDirectory] block tells the
            // reader nothing below was checked, and there is no way to know there is anything
            // below.
            violations += UncheckedFile(path = path, cause = cause)
            return
        }
        catching {
            if (isDirectory) visitDirectory(child, path) else visitFile(path)
        }.onFailure { cause ->
            violations += if (isDirectory) {
                UncheckedDirectory(
                    path = path,
                    reason = UncheckedDirectoryReason.NotWalked,
                    cause = cause,
                )
            } else {
                UncheckedFile(path = path, cause = cause)
            }
        }
    }

    private fun visitDirectory(directory: FsPath, path: String) {
        if (directory.name in FOREIGN_DIRECTORY_NAMES) return
        if (layout.isIgnored(path)) return
        if (!layout.isKnown(path)) {
            violations += UnexpectedDirectory(path)
            return
        }
        walk(directory, path)
    }

    private fun visitFile(path: String) {
        // Recorded before the question is asked, so that a file the check failed at is not
        // also reported as missing: one path never earns two blocks.
        visitedFiles += path
        val roles = layout.rolesOf(path)
        // Every role that allows the file, not just the first: see [LayoutIndex.rolesOf].
        for (role in roles) filesByRole.getOrPut(role) { mutableListOf() } += path
        // Only when two roles already allow it. `claimsOn` answers out of a subset of what
        // `rolesOf` reads, so one role here can never be two there, and the ordinary file
        // pays for one comparison instead of a second pass over every declaration.
        if (roles.size > 1) fileOverlaps.record(path, layout.claimsOn(path))
        if (roles.isNotEmpty()) return
        violations += UnexpectedFile(path = path, nearby = layout.nearbyOf(path))
    }
}
