package me.tbsten.katachi.intellij.data.generate

import me.tbsten.katachi.intellij.data.ProjectFileSystem
import me.tbsten.katachi.intellij.data.placement.placementRootOf
import me.tbsten.katachi.intellij.model.ModuleTemplate
import me.tbsten.katachi.intellij.presentation.OnExistingChoice
import me.tbsten.katachi.intellij.presentation.dialog.dialogTargetOf
import java.nio.file.Path

// The target and the arguments of a generation from an entry (SingleFileGeneration), apart from its flow.

/**
 * The `--arg`s of [request]: `template` (its one complete specifier, issue 2) first, then the dialog's
 * captures and parameters. `onExisting` is left to the run, which sets it per attempt.
 */
internal fun entryArgsOf(request: SingleFileGenerationRequest): List<Pair<String, String>> =
    listOf(TEMPLATE_ARG to request.template.template.template) +
        request.args.filter { (name, _) -> name != TEMPLATE_ARG && name != ON_EXISTING_ARG }

/** [this] with `onExisting=overwrite` after `template`, as the run passes them (decision 18). */
internal fun List<Pair<String, String>>.withOverwrite(): List<Pair<String, String>> =
    take(1) + (ON_EXISTING_ARG to OnExistingChoice.Overwrite.argValue) + drop(1)

/**
 * Where [template] (as re-read) writes with [request]'s inputs, by the same rule the dialog showed
 * (`dialogTargetOf`): the target of [request] when the definition did not move it; `null` when it is
 * no longer decided.
 */
internal fun entryTargetOf(template: ModuleTemplate, request: SingleFileGenerationRequest, fileSystem: ProjectFileSystem): Path? {
    val detail = template.template.detail ?: return null
    val inputs = request.args.toMap()
    // The inputs as seeds: a module-derived `<x>` keeps the origin's spelling, as it did in the dialog.
    return dialogTargetOf(detail, inputs, inputs, placementRootOf(template.module, fileSystem), request.origin.path).absolute
}

/**
 * The module directory a module capture of [template] names in [target], when it is not a module
 * (no directory, or no build script in it); `null` when there is none to check or it exists.
 * katachi does not create modules (spike S1 §3), so no directory is created in its place.
 */
internal fun missingModuleOf(template: ModuleTemplate, target: Path, fileSystem: ProjectFileSystem): Path? {
    val detail = template.template.detail ?: return null
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

/** [directory] and its ancestors that do not exist yet, nearest first. */
internal fun missingDirectoriesOf(directory: Path, fileSystem: ProjectFileSystem): List<Path> =
    generateSequence(directory) { it.parent }.takeWhile { !fileSystem.exists(it) }.toList()

private const val TEMPLATE_ARG = "template"
private const val ON_EXISTING_ARG = "onExisting"
private val BUILD_SCRIPTS = listOf("build.gradle.kts", "build.gradle")
