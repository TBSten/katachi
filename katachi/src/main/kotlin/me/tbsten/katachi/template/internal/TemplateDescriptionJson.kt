package me.tbsten.katachi.template.internal

import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileSystemException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.serialization.Serializable
import me.tbsten.katachi.template.KatachiTemplateJsonIoException
import me.tbsten.katachi.template.TemplateDetail
import me.tbsten.katachi.template.TemplateSummary

/**
 * The whole of `templateDescription.json`: the list, the detail of every template whose preview
 * succeeded, and where the file of each of those that sits below a module capture lands in every
 * module it can pick. A template whose preview failed is in [templates] with `conflict: true` and
 * missing from [details].
 */
@Serializable
internal class TemplateDescriptionJson(
    val templates: List<TemplateSummary>,
    val details: List<TemplateDetail>,
    /**
     * One entry per template of [details] whose file sits below a module capture, which leaves its
     * `files[].path` `null`. See [TemplateModulePlacement].
     */
    val modulePlacements: List<TemplateModulePlacement>,
)

/** [templates], [details] and [modulePlacements] as the text of `templateDescription.json`. */
internal fun encodeTemplateDescriptionJson(
    templates: List<TemplateSummary>,
    details: List<TemplateDetail>,
    modulePlacements: List<TemplateModulePlacement> = emptyList(),
): String = encodeToJson(
    TemplateDescriptionJson.serializer(),
    TemplateDescriptionJson(templates, details, modulePlacements),
)

/** The suffix the JSON wears while it is written but not yet in place. */
private const val STAGING_SUFFIX: String = ".katachi-new"

/**
 * Writes [json] to [output], creating its directory, and replaces the file in one move.
 *
 * The IDE plugin reads this file while Gradle may be writing it. Writing next to it and moving
 * over it means a reader sees the old file or the new one, never half of either.
 *
 * @param output the path as `--arg output=` spelt it, relative to the working directory or absolute.
 */
internal fun writeTemplateDescriptionJson(output: String, json: String) {
    val target = File(output).absoluteFile
    val staging = File(target.path + STAGING_SUFFIX)
    try {
        // An empty directory would otherwise be replaced by the move without a word.
        if (target.isDirectory) throw FileSystemException(target.path, null, "is a directory")
        target.parentFile?.let { Files.createDirectories(it.toPath()) }
        staging.writeText(json, Charsets.UTF_8)
        try {
            Files.move(
                staging.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (notAtomic: AtomicMoveNotSupportedException) {
            // A file system without an atomic rename still replaces the file in one call.
            Files.move(staging.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    } catch (failure: IOException) {
        throw jsonIoFailure(staging, output, failure)
    } catch (failure: SecurityException) {
        // A security manager refusing the write, as TemplateOutput and DocumentFiles also report it.
        throw jsonIoFailure(staging, output, failure)
    }
}

/** Removes the half-written [staging] file and names [output] in the exception. */
private fun jsonIoFailure(staging: File, output: String, failure: Exception): KatachiTemplateJsonIoException {
    runCatching { staging.delete() }
    return KatachiTemplateJsonIoException(output = output, cause = failure)
}
