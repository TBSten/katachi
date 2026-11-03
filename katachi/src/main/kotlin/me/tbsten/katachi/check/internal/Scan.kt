package me.tbsten.katachi.check.internal

import me.tbsten.katachi.check.CaptureMismatch
import me.tbsten.katachi.check.CaptureOccurrence
import me.tbsten.katachi.check.MissingFile
import me.tbsten.katachi.check.UncheckedDirectory
import me.tbsten.katachi.check.UncheckedDirectoryReason
import me.tbsten.katachi.check.UncheckedFile
import me.tbsten.katachi.check.UnexpectedDirectory
import me.tbsten.katachi.check.UnexpectedFile
import me.tbsten.katachi.check.Violation
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.LayoutEntry
import me.tbsten.katachi.dsl.Role
import me.tbsten.katachi.dsl.files.FsPath
import me.tbsten.katachi.dsl.files.KatachiFileSystem
import me.tbsten.katachi.dsl.files.internal.OutsideProjectDirectories
import me.tbsten.katachi.dsl.files.internal.findProjectRoot
import me.tbsten.katachi.dsl.internal.DeclaredFileConstraint
import me.tbsten.katachi.dsl.internal.LayoutEvaluation
import me.tbsten.katachi.dsl.internal.evaluateLayout
import me.tbsten.katachi.dsl.internal.fillCaptures
import me.tbsten.katachi.dsl.internal.placeholder
import me.tbsten.katachi.internal.catching

// The only name skipped at any depth regardless of `files` (a file in worktrees and submodules).
private const val GIT_DIRECTORY_NAME: String = ".git"

// The root relative to itself is the empty string, which would print as a label with nothing after it.
internal const val ROOT_PATH: String = "."

internal class ScanResult(
    val violations: List<Violation>,
    val filesByRole: Map<Role, List<String>>,
    val fileOverlaps: List<FileOverlap>,
    val fileConstraints: List<DeclaredFileConstraint>,
    val layout: LayoutEvaluation,
    val projectRoot: FsPath,
)

internal fun Architecture.scanProject(
    fileSystem: KatachiFileSystem,
    reusing: LayoutEvaluation? = null,
): ScanResult {
    val projectRoot = findProjectRoot(fileSystem)
    val selected = files.fileSystemFor(fileSystem, projectRoot)
    val modules = scanModules(
        fileSystem = fileSystem,
        projectRoot = projectRoot.path,
        skips = if (selected is OutsideProjectDirectories) selected::holdsNothingOfTheProject else { _ -> false },
    )
    // One evaluation of the layout, read from both sides: the entries drive the walk, the
    // constraints ride along to whichever check evaluates them. Flattening twice would be a
    // second chance for the two to disagree about what a wildcard module key expanded to.
    val evaluation = evaluateLayout(modules.indexWith(moduleResolver), reusing)
    return Scan(
        fileSystem = selected,
        root = projectRoot.path,
        evaluation = evaluation,
        moduleFailures = modules.unchecked,
    ).run()
}

private class Scan(
    private val fileSystem: KatachiFileSystem,
    private val root: FsPath,
    private val evaluation: LayoutEvaluation,
    private val moduleFailures: List<UncheckedDirectory>,
) {
    private val layout = LayoutIndex(evaluation.entries)

    private val violations = mutableListOf<Violation>()

    private val visitedFiles = mutableSetOf<String>()

    private val filesByRole = LinkedHashMap<Role, MutableList<String>>()

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
            fileConstraints = evaluation.fileConstraints,
            layout = evaluation,
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

    private fun visitChild(child: FsPath, path: String) {
        // Before asking whether it is a directory: in a worktree or a submodule `.git` is a file.
        if (child.name == GIT_DIRECTORY_NAME) return
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
        // A pattern did match, and only a bound capture held the file back: that is a more
        // useful thing to say than "no role", and it names where the file should be.
        val (entry, mismatch) = layout.captureMismatchesOn(path).firstOrNull() ?: run {
            violations += UnexpectedFile(path = path, nearby = layout.nearbyOf(path))
            return
        }
        violations += CaptureMismatch(
            path = path,
            role = entry.role,
            declaredAt = entry.declaredAt,
            pattern = fillCaptures(entry.path, mismatch.variant.pathCaptures) { it.placeholder() },
            occurrences = mismatch.occurrences.map {
                CaptureOccurrence(name = it.name, transform = it.transform?.spelling, level = it.level, value = it.value, expected = it.expected)
            },
            expectedPath = mismatch.expectedPath,
        )
    }
}
