package me.tbsten.katachi.template.internal

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import me.tbsten.katachi.internal.absolutePathOf
import me.tbsten.katachi.internal.displayExistingPath
import me.tbsten.katachi.template.KatachiExistingTemplateFileException
import me.tbsten.katachi.template.KatachiReservedTemplatePathException
import me.tbsten.katachi.template.KatachiTemplateEscapesProjectException
import me.tbsten.katachi.template.KatachiTemplateIoException
import me.tbsten.katachi.template.KatachiTemplateTargetNotAFileException
import me.tbsten.katachi.template.OnExisting

private const val STAGING_SUFFIX: String = ".katachi-new"

private const val BACKUP_SUFFIX: String = ".katachi-old"

private class StagedFile(
    val path: String,
    val target: File,
    val staging: File,
    val backup: File,
)

// All files or none: each is staged next to its target, installed, and rolled back on failure.
internal fun writeTemplateFiles(
    projectRoot: File,
    files: Map<String, String>,
    onExisting: OnExisting,
    log: (String) -> Unit,
): Boolean {
    // Only what the log prints resolves against this. The writing itself keeps using [projectRoot].
    val root = absolutePathOf(projectRoot.path)
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
                    "(${existing.joinToString(", ") { displayExistingPath(root, it) }}).",
            )
            return false
        }

        OnExisting.Overwrite -> if (existing.isNotEmpty()) {
            log(
                "Overwriting ${existing.size} of ${files.size} files: " +
                    existing.joinToString(", ") { displayExistingPath(root, it) },
            )
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

    for (path in files.keys) log("Wrote ${displayExistingPath(root, path)}")
    return true
}

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

private fun move(path: String, projectRoot: File, from: File, to: File) {
    catchingIo(path, projectRoot) {
        Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
}

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

private fun <T> catchingIo(path: String, projectRoot: File, block: () -> T): T = try {
    block()
} catch (cause: IOException) {
    throw KatachiTemplateIoException(path = path, projectRoot = projectRoot.path, cause = cause)
} catch (cause: SecurityException) {
    throw KatachiTemplateIoException(path = path, projectRoot = projectRoot.path, cause = cause)
}
