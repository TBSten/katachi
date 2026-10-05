package me.tbsten.katachi.template.internal

import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileSystemException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.serialization.Serializable
import me.tbsten.katachi.check.internal.moduleIndex
import me.tbsten.katachi.dsl.files.internal.findProjectRoot
import me.tbsten.katachi.internal.catching
import me.tbsten.katachi.processor.ArchitectureProcessContext
import me.tbsten.katachi.processor.internal.fileSystem
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

/** [json] as the text of `templateDescription.json`. */
internal fun encodeTemplateDescriptionJson(json: TemplateDescriptionJson): String =
    encodeToJson(TemplateDescriptionJson.serializer(), json)

/**
 * Every template of [table], listed, explained and placed: what `templateDescription.json` holds,
 * and what the definition JSON carries under its `templates` key.
 *
 * One template that cannot be previewed must not cost the IDE every other one. It is still
 * listed, with `conflict: true`, as the text list already shows it. The modules are read only when
 * a template sits below a module capture: which modules exist is what decides where such a file
 * lands, and the declarations alone cannot say it. Without [withPlacements] they are not read, and
 * `modulePlacements` is empty.
 */
internal fun templateDescriptionJsonOf(
    context: ArchitectureProcessContext<*>,
    table: List<DeclaredTemplate> = declaredTemplatesOf(context.declaredEntries),
    withPlacements: Boolean = true,
): TemplateDescriptionJson {
    val previewed = table.mapNotNull { template -> catching { template to templateDetailOf(template) }.getOrNull() }
    val placements = if (!withPlacements) {
        emptyList()
    } else {
        modulePlacementsOf(previewed.map { it.first }) {
            moduleIndex(context.fileSystem, findProjectRoot(context.fileSystem).path, context.architecture.moduleResolver)
        }
    }
    return TemplateDescriptionJson(
        templates = table.map(::templateSummaryOf),
        details = previewed.map { it.second },
        modulePlacements = placements,
    )
}

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
    writeFileAtomically(output, json) { failure -> KatachiTemplateJsonIoException(output = output, cause = failure) }
}

/**
 * Writes [text] to [output] the way [writeTemplateDescriptionJson] does -- next to it first, then
 * moved over it in one call -- and throws what [failureOf] makes of a refusal, the half-written
 * staging file removed. Shared with the definition JSON (`DescribeDefinition`), which the IDE
 * plugin reads while Gradle writes it in the same way.
 *
 * @param output the path as `--arg output=` spelt it, relative to the working directory or absolute.
 */
internal fun writeFileAtomically(output: String, text: String, failureOf: (Exception) -> Exception) {
    val target = File(output).absoluteFile
    val staging = File(target.path + STAGING_SUFFIX)
    try {
        // An empty directory would otherwise be replaced by the move without a word.
        if (target.isDirectory) throw FileSystemException(target.path, null, "is a directory")
        target.parentFile?.let { Files.createDirectories(it.toPath()) }
        staging.writeText(text, Charsets.UTF_8)
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
        runCatching { staging.delete() }
        throw failureOf(failure)
    } catch (failure: SecurityException) {
        // A security manager refusing the write, as TemplateOutput and DocumentFiles also report it.
        runCatching { staging.delete() }
        throw failureOf(failure)
    }
}
