package me.tbsten.katachi.template.internal

import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileSystemException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.serialization.Serializable
import me.tbsten.katachi.internal.catching
import me.tbsten.katachi.processor.ArchitectureProcessContext
import me.tbsten.katachi.template.KatachiTemplateJsonIoException
import me.tbsten.katachi.template.TemplateDetail
import me.tbsten.katachi.template.TemplateSetDetail
import me.tbsten.katachi.template.TemplateSetSummary
import me.tbsten.katachi.template.TemplateSummary

@Serializable
internal class TemplateDescriptionJson(
    val templates: List<TemplateSummary>,
    val details: List<TemplateDetail>,
    val templateSets: List<TemplateSetSummary> = emptyList(),
    val templateSetDetails: List<TemplateSetDetail> = emptyList(),
)

internal fun encodeTemplateDescriptionJson(
    templates: List<TemplateSummary>,
    details: List<TemplateDetail>,
    templateSets: List<TemplateSetSummary> = emptyList(),
    templateSetDetails: List<TemplateSetDetail> = emptyList(),
): String = encodeToJson(
    TemplateDescriptionJson.serializer(),
    TemplateDescriptionJson(templates, details, templateSets, templateSetDetails),
)

internal fun encodeTemplateDescriptionJson(json: TemplateDescriptionJson): String =
    encodeToJson(TemplateDescriptionJson.serializer(), json)

internal fun templateDescriptionJsonOf(
    context: ArchitectureProcessContext<*>,
    table: List<DeclaredTemplate> = declaredTemplatesOf(context.declaredEntries),
): TemplateDescriptionJson = templateDescriptionOf(context, table).json

internal class TemplateDescription(
    val json: TemplateDescriptionJson,
    val templateFailures: Map<String, Throwable>,
    val templateSetFailures: Map<String, Throwable>,
)

internal fun templateDescriptionOf(
    context: ArchitectureProcessContext<*>,
    table: List<DeclaredTemplate> = declaredTemplatesOf(context.declaredEntries),
): TemplateDescription {
    val templateFailures = LinkedHashMap<String, Throwable>()
    val previewed = table.mapNotNull { template ->
        catching { template to templateDetailOf(template) }
            .onFailure { templateFailures[template.specifier] = it }
            .getOrNull()
    }
    val sets = context.architecture.allTemplateSets
    val templateSetFailures = LinkedHashMap<String, Throwable>()
    val json = TemplateDescriptionJson(
        templates = table.map(::templateSummaryOf),
        details = previewed.map { it.second },
        templateSets = sets.map { templateSetSummaryOf(it, table) },
        templateSetDetails = sets.mapNotNull { set ->
            catching { templateSetDetailOf(set, table) }
                .onFailure { templateSetFailures[set.specifier] = it }
                .getOrNull()
        },
    )
    return TemplateDescription(json, templateFailures, templateSetFailures)
}

private const val STAGING_SUFFIX: String = ".katachi-new"

internal fun writeTemplateDescriptionJson(output: String, json: String) {
    writeFileAtomically(output, json) { failure -> KatachiTemplateJsonIoException(output = output, cause = failure) }
}

// Written next to the target first, then moved over it in one call, so a refused write leaves no half-written file.
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
