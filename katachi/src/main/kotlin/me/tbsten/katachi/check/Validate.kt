package me.tbsten.katachi.check

import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.InternalKatachiApi
import me.tbsten.katachi.dsl.LayoutEntry
import me.tbsten.katachi.dsl.Role
import me.tbsten.katachi.dsl.flattenLayout
import me.tbsten.katachi.processor.process

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
 * nothing after the label.
 */
private const val ROOT_PATH: String = "."

/**
 * Runs the check against the real file system and returns every violation.
 *
 * This is [LayoutCheck] run through `process`, kept as a name of its own because it is the
 * shape the check has always had here and nothing about it needs to change: a caller who wants
 * the violations of the layout says so, without having to know that the check became one
 * processor among others underneath.
 *
 * Nothing is thrown, including when the definition allows nothing at all: `assert()` is what
 * turns this into a test failure, and a report or a baseline can sit on it instead.
 *
 * @throws KatachiProjectRootNotFoundException when no directory above the working directory
 *   carries a Gradle, Maven or git marker.
 * @throws KatachiGitUnavailableException when `files = gitTracked()` and the project root is a git
 *   repository, but git cannot be run.
 */
@InternalKatachiApi
public fun Architecture.validate(): List<Violation> = validate(RealFileSystem())

/**
 * [validate] against [fileSystem], which katachi's own specs use to hand it a tree that only
 * exists in memory.
 *
 * The project root is looked for in [fileSystem] itself, unfiltered: a marker such as
 * `.git/HEAD` belongs to none of the file sets `files` can select, so the search has to run
 * before the selection is applied. The module index is built the same way, because a module
 * whose `build.gradle.kts` a file set happens to leave out is still a module, and a module
 * path key with a wildcard would otherwise quietly expand to nothing.
 *
 * Nothing in here is caught, unlike the walk it hands over to. Finding the root, selecting
 * the files and evaluating the definition each produce the one thing the walk needs, so a
 * failure in any of them leaves nothing to check and nothing to report — there is no
 * "the rest of the run" to save.
 *
 * The walk is reached through [LayoutCheck] and the model it is handed, which is also why
 * everything above happens when this is called rather than when the model is built: the check
 * asks for the violations straight away, and asking is what starts the walk.
 */
@InternalKatachiApi
public fun Architecture.validate(fileSystem: KatachiFileSystem): List<Violation> =
    process(LayoutCheck(), fileSystem)

/**
 * Everything one walk of the tree produced.
 *
 * The walk answers two questions at once, and it has to: which files nobody declared, and
 * which role each file that was declared belongs to. They are the same traversal seen from
 * either side, so running it twice would be the same work done twice and, worse, two chances
 * for the two answers to stop agreeing.
 */
internal class ScanResult(
    /** What [Architecture.validate] returns, already ordered by block kind. */
    val violations: List<Violation>,
    /**
     * Files that exist, per role that allows them, in walk order. A file two roles both allow
     * is listed under both; a role whose declarations matched nothing is absent rather than
     * present with an empty list, which is why reads go through `orEmpty()`.
     */
    val filesByRole: Map<Role, List<String>>,
)

/**
 * The one walk everything is built on, reached through
 * [me.tbsten.katachi.processor.ProjectModel] — which runs it at most once, so that a run
 * answering both "what is wrong" and "what does this role own" walks the tree once. See
 * [validate] for what is and is not caught here.
 */
internal fun Architecture.scanProject(fileSystem: KatachiFileSystem): ScanResult {
    val projectRoot = findProjectRoot(fileSystem)
    return Scan(
        fileSystem = files.fileSystemFor(fileSystem, projectRoot),
        root = projectRoot.path,
        layout = LayoutIndex(flattenLayout(moduleIndex(fileSystem, projectRoot.path, moduleResolver))),
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
) {
    private val violations = mutableListOf<Violation>()

    /** Every file the walk reached, as a project relative path. */
    private val visitedFiles = mutableSetOf<String>()

    /** The files each role turned out to own, in walk order. See [ScanResult.filesByRole]. */
    private val filesByRole = LinkedHashMap<Role, MutableList<String>>()

    fun run(): ScanResult {
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
            violations += UncheckedDirectory(path = path.ifEmpty { ROOT_PATH }, cause = cause)
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
                UncheckedDirectory(path = path, cause = cause)
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
        if (roles.isNotEmpty()) return
        violations += UnexpectedFile(path = path, nearby = layout.nearbyOf(path))
    }
}

/**
 * Whether the check has to end instead of turning this into a violation.
 *
 * `runCatching` swallows every [Throwable], which is more than the walk is entitled to decide
 * about. The four below are the ones where carrying on either cannot work or destroys
 * something, and they are listed here rather than at each call site so that every unit of the
 * walk treats a failure the same way.
 *
 * A [me.tbsten.katachi.dsl.KatachiInternalException] is deliberately not among them. It is a
 * katachi bug, but turning one path into an `[UncheckedFile]` and checking the rest hands the
 * reader more than failing the whole run would.
 */
private val Throwable.isFatal: Boolean
    get() = when (this) {
        // The JVM cannot carry on. Catching one only loses the cause: the next path runs into
        // the same wall with less information attached.
        is VirtualMachineError -> true
        // The classpath is broken, so every path fails the same way and the report fills up
        // with copies of one problem instead of the violations it was asked for.
        is LinkageError -> true
        // The caller asked for this to stop. Swallowing that is ignoring it.
        is InterruptedException -> true
        // A finished check, not a failed one.
        is KatachiArchitectureAssertionError -> true
        else -> false
    }

/**
 * Runs [block] and keeps whatever it threw, unless [isFatal] says the run has to end.
 *
 * Written with `is` rather than `as`: nothing is cast here, the type is only asked about.
 */
private inline fun <T> catching(block: () -> T): Result<T> =
    runCatching(block).onFailure { if (it.isFatal) throw it }
