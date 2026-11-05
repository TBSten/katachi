package me.tbsten.katachi.intellij.data.generate

import me.tbsten.katachi.intellij.data.ProjectFileSystem
import me.tbsten.katachi.intellij.data.placement.placementRootOf
import me.tbsten.katachi.intellij.model.ModuleTemplate
import me.tbsten.katachi.intellij.presentation.OnExistingChoice
import me.tbsten.katachi.intellij.presentation.dialog.dialogTargetOf
import java.nio.file.Path

// The target and the arguments of a generation from an entry (SingleFileGeneration), apart from its flow.

internal fun entryArgsOf(request: SingleFileGenerationRequest): List<Pair<String, String>> =
    listOf(TEMPLATE_ARG to request.template.template.template) +
        request.args.filter { (name, _) -> name != TEMPLATE_ARG && name != ON_EXISTING_ARG }

internal fun List<Pair<String, String>>.withOverwrite(): List<Pair<String, String>> =
    take(1) + (ON_EXISTING_ARG to OnExistingChoice.Overwrite.argValue) + drop(1)

internal fun entryTargetOf(template: ModuleTemplate, request: SingleFileGenerationRequest, fileSystem: ProjectFileSystem): Path? {
    val detail = template.template.detail ?: return null
    val inputs = request.args.toMap()
    // The inputs as seeds: a module-derived `<x>` keeps the origin's spelling, as it did in the dialog.
    return dialogTargetOf(detail, inputs, inputs, placementRootOf(template.module, fileSystem), request.origin.path).absolute
}

internal fun missingModuleOf(template: ModuleTemplate, target: Path, fileSystem: ProjectFileSystem): Path? {
    val detail = template.template.detail ?: return null
    // The module katachi listed for the target, at the directory it gave: no guessing from the pattern.
    val placed = detail.files.firstNotNullOfOrNull { it.modulePlacement }?.let { placement ->
        val root = placementRootOf(template.module, fileSystem)
        placement.modules.filter { it.directory.isNotEmpty() && target.startsWith(root.resolve(it.directory)) }
            .maxByOrNull { it.directory.length }?.let { root.resolve(it.directory) }
    }
    if (placed != null) return placed.takeUnless { directory -> BUILD_SCRIPTS.any { fileSystem.exists(directory.resolve(it)) } }
    val moduleCaptures = detail.captures.filter { capture -> capture.places.any { it.isModule } }.map { it.name }
    if (moduleCaptures.isEmpty()) return null
    val segments = detail.files.firstOrNull()?.pattern?.split('/') ?: return null
    val last = segments.indexOfLast { segment -> moduleCaptures.any { segment.contains("\${$it}") } }
    if (last < 0) return null
    val root = placementRootOf(template.module, fileSystem)
    if (!target.startsWith(root)) return null
    val relative = root.relativize(target)
    if (relative.nameCount <= last + 1) return null
    val moduleDirectory = root.resolve(relative.subpath(0, last + 1))
    return moduleDirectory.takeUnless { directory -> BUILD_SCRIPTS.any { fileSystem.exists(directory.resolve(it)) } }
}

internal fun missingDirectoriesOf(directory: Path, fileSystem: ProjectFileSystem): List<Path> =
    generateSequence(directory) { it.parent }.takeWhile { !fileSystem.exists(it) }.toList()

private const val TEMPLATE_ARG = "template"
private const val ON_EXISTING_ARG = "onExisting"
private val BUILD_SCRIPTS = listOf("build.gradle.kts", "build.gradle")
