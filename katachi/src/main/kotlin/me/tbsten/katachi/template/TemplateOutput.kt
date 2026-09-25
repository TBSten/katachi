package me.tbsten.katachi.template

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** The suffix a file wears while it is written but not yet in place. */
private const val STAGING_SUFFIX: String = ".katachi-new"

/** The suffix the file being replaced wears until the whole set is in place. */
private const val BACKUP_SUFFIX: String = ".katachi-old"

/** One file of a run, the temporary it is written through, and where its predecessor waits. */
private class StagedFile(
    val path: String,
    val target: File,
    val staging: File,
    val backup: File,
)

/**
 * Puts [files] into [projectRoot], **all of them or none**, and says through [log] what it did.
 *
 * ## Why nothing is written before everything can be
 *
 * This writes into the user's own source tree, where a half-applied template is worse than no
 * template at all: the files that landed look generated and the ones that did not are missing, and
 * nothing on disk says which run left it that way. So the run is decided before the first byte
 * moves -- every target is checked for an existing file, and [onExisting] answers for the whole
 * set rather than per file -- and the contents are then written to `.katachi-new` files and moved
 * into place only once every one of them is on disk.
 *
 * ## How replacing stays undoable
 *
 * Moving the last staged file into place can still fail once an earlier one has been replaced, so
 * a file that is about to be overwritten is first moved aside to `.katachi-old`. Only when every
 * target is in place are those removed. A failure anywhere in between puts the originals back.
 * Should putting them back fail too -- the disk filled, the volume went away -- the contents are
 * still on disk under `.katachi-old`, which is recoverable in a way an overwritten file is not.
 *
 * Both suffixes are katachi's, and a run stops before writing anything if the user's tree already
 * has a file at one of them, rather than treating it as scratch. A target that is a directory
 * stops the run for the same reason: putting it back is not something this can promise.
 *
 * Deleting is never part of it. `DocumentFiles.writeDocuments` removes the pages a run no longer
 * produces, which is right for a directory that is katachi's; here the neighbours of a generated
 * file are the user's own code.
 *
 * @return whether anything was written. `false` only when [OnExisting.Skip] found something.
 */
internal fun writeTemplateFiles(
    projectRoot: File,
    files: Map<String, String>,
    onExisting: OnExisting,
    log: (String) -> Unit,
): Boolean {
    val blocked = files.keys
        .filter { File(projectRoot, it).let { target -> target.exists() && !target.isFile } }
        .sorted()
    if (blocked.isNotEmpty()) {
        throw KatachiTemplateTargetNotAFileException(
            blocked = blocked,
            projectRoot = projectRoot.path,
        )
    }

    val existing = files.keys.filter { File(projectRoot, it).exists() }.sorted()
    when (onExisting) {
        OnExisting.Fail -> if (existing.isNotEmpty()) {
            throw KatachiExistingTemplateFileException(
                existing = existing,
                total = files.size,
                projectRoot = projectRoot.path,
            )
        }

        OnExisting.Skip -> if (existing.isNotEmpty()) {
            log(
                "Wrote nothing: ${existing.size} of ${files.size} files are already there " +
                    "(${existing.joinToString(", ")}).",
            )
            return false
        }

        OnExisting.Overwrite -> if (existing.isNotEmpty()) {
            log("Overwriting ${existing.size} of ${files.size} files: ${existing.joinToString(", ")}")
        }
    }

    val reserved = files.keys
        .flatMap { listOf(it + STAGING_SUFFIX, it + BACKUP_SUFFIX) }
        .filter { File(projectRoot, it).exists() }
        .sorted()
    if (reserved.isNotEmpty()) {
        throw KatachiReservedTemplatePathException(
            reserved = reserved,
            projectRoot = projectRoot.path,
        )
    }

    for (path in files.keys) requireUnderRoot(projectRoot, path)

    val staged = mutableListOf<StagedFile>()
    val backedUp = mutableListOf<StagedFile>()
    val installed = mutableListOf<StagedFile>()
    try {
        for ((path, content) in files) staged += stage(projectRoot, path, content)
        for (file in staged) {
            if (!file.target.exists()) continue
            move(file.path, projectRoot, from = file.target, to = file.backup)
            backedUp += file
        }
        for (file in staged) {
            move(file.path, projectRoot, from = file.staging, to = file.target)
            installed += file
        }
    } catch (cause: Throwable) {
        undo(installed = installed, backedUp = backedUp)
        throw cause
    } finally {
        // Whatever is left has either been moved away already or belongs to a run that failed.
        for (file in staged) file.staging.delete()
        for (file in backedUp) file.backup.delete()
    }

    for (path in files.keys) log("Wrote $path")
    return true
}

/**
 * Refuses [path] when the file system puts it outside [projectRoot] after all.
 *
 * `placeTemplateFile` already refuses a path that climbs out of the project, but it reads the text
 * of the path and nothing else -- it is a pure function, and being one is what lets a spec pin
 * every placement without a tree on disk. A symbolic link is invisible to that: `src/main/kotlin`
 * pointing elsewhere sends the file out of the project while the path stays relative, and the log
 * still reports the declared path. This is the same question asked once more of the disk, at the
 * only place that can ask it.
 *
 * The nearest ancestor that exists is what gets resolved. The file itself usually does not exist
 * yet, and neither may the directories a template is about to create, but a link can only be one
 * of the parts that are already there.
 */
private fun requireUnderRoot(projectRoot: File, path: String) {
    val root = catchingIo(path, projectRoot) { projectRoot.canonicalFile }
    var probe: File? = File(projectRoot, path).parentFile
    while (probe != null && !probe.exists()) probe = probe.parentFile
    val resolved = catchingIo(path, projectRoot) { (probe ?: root).canonicalFile }
    if (resolved == root || resolved.path.startsWith(root.path + File.separator)) return
    throw KatachiTemplateEscapesProjectException(
        path = path,
        resolved = resolved.path,
        projectRoot = root.path,
    )
}

/** Writes one file's contents next to where it belongs, under a name nothing else will claim. */
private fun stage(projectRoot: File, path: String, content: String): StagedFile {
    val target = File(projectRoot, path)
    val staging = File(target.parentFile, target.name + STAGING_SUFFIX)
    catchingIo(path, projectRoot) {
        target.parentFile?.mkdirs()
        staging.writeText(content)
    }
    return StagedFile(
        path = path,
        target = target,
        staging = staging,
        backup = File(target.parentFile, target.name + BACKUP_SUFFIX),
    )
}

/**
 * Moves one file onto another, replacing what is there.
 *
 * `Files.move` rather than `File.renameTo`: renaming onto an existing file fails on Windows, and
 * `renameTo` reports a refusal by returning `false` rather than by saying what went wrong.
 */
private fun move(path: String, projectRoot: File, from: File, to: File) {
    catchingIo(path, projectRoot) {
        Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
}

/**
 * Puts the tree back the way the run found it, as far as the filesystem still allows.
 *
 * The originals are restored last so that a target created by this run is gone before its
 * predecessor moves back onto the same name. A move that fails here is left alone rather than
 * retried: the caller is already carrying the failure that started the unwind, and replacing it
 * with one from the cleanup would hide what actually went wrong. What the user is left with is a
 * `.katachi-old` file whose name says what it is.
 */
private fun undo(installed: List<StagedFile>, backedUp: List<StagedFile>) {
    for (file in installed) file.target.delete()
    for (file in backedUp) {
        try {
            Files.move(file.backup.toPath(), file.target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } catch (ignored: IOException) {
            // The contents stay under .katachi-old, which the exception's own message points at.
        } catch (ignored: SecurityException) {
            // The same, for a tree this process may no longer write to.
        }
    }
}

/**
 * Runs [block], turning whatever the filesystem refuses into an exception naming the file.
 *
 * A bare `FileNotFoundException` names an absolute path and nothing else, so a reader cannot tell
 * a read-only checkout from a layout that resolved somewhere they did not expect.
 */
private fun <T> catchingIo(path: String, projectRoot: File, block: () -> T): T = try {
    block()
} catch (cause: IOException) {
    throw KatachiTemplateIoException(path = path, projectRoot = projectRoot.path, cause = cause)
} catch (cause: SecurityException) {
    throw KatachiTemplateIoException(path = path, projectRoot = projectRoot.path, cause = cause)
}
