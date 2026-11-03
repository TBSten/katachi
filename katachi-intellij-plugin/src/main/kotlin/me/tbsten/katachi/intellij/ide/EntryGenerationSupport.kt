package me.tbsten.katachi.intellij.ide

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import me.tbsten.katachi.intellij.data.generate.EntryGenerationFailure
import me.tbsten.katachi.intellij.data.generate.EntryGenerationRefusal
import me.tbsten.katachi.intellij.data.generate.EntryIdeAction
import me.tbsten.katachi.intellij.model.GenerationFailure
import java.nio.file.Path

// What IdeEffectsImpl needs for the generation from an entry beyond the SDK calls themselves.

internal fun entryFailureText(failure: EntryGenerationFailure): String = when (failure) {
    is EntryGenerationFailure.CatalogReloadFailed -> KatachiBundle.message("generate.failed.catalogReload")
    EntryGenerationFailure.TemplateGone -> KatachiBundle.message("generate.failed.templateGone")
    is EntryGenerationFailure.TargetMoved -> KatachiBundle.message("generate.failed.targetMoved")
    is EntryGenerationFailure.ChangedMeanwhile -> KatachiBundle.message("generate.failed.changedMeanwhile", failure.target.fileName.toString())
    is EntryGenerationFailure.Katachi -> KatachiBundle.message("generate.failed.katachi", reasonOf(failure.failure))
    is EntryGenerationFailure.Ide -> KatachiBundle.message("generate.failed.ide", ideActionText(failure.action))
}

internal fun entryRefusalText(refusal: EntryGenerationRefusal): String = KatachiBundle.message(
    "dialog.generateRefused",
    when (refusal) {
        is EntryGenerationRefusal.TargetHasContent -> KatachiBundle.message("dialog.target.hasContent")
        is EntryGenerationRefusal.ModuleMissing -> KatachiBundle.message("generate.refused.moduleMissing", refusal.directory.fileName.toString())
    },
)

private fun reasonOf(failure: GenerationFailure): String = when (failure) {
    is GenerationFailure.Katachi -> failure.body.firstOrNull { it.isNotBlank() }?.trim()
    is GenerationFailure.NotReached -> failure.failure.details.firstOrNull { it.isNotBlank() }?.trim()
}.orEmpty()

private fun ideActionText(action: String): String = when (action) {
    EntryIdeAction.SAVE -> KatachiBundle.message("generate.failed.ide.save")
    EntryIdeAction.CREATE_DIRECTORIES -> KatachiBundle.message("generate.failed.ide.createDirectories")
    EntryIdeAction.WRITE_PROVISIONAL -> KatachiBundle.message("generate.failed.ide.writeProvisional")
    else -> action
}

internal fun packageOfDirectory(project: Project, directory: Path): String? {
    val fileSystem = LocalFileSystem.getInstance()
    var known: VirtualFile? = null
    var current: Path? = directory
    val missing = ArrayDeque<String>()
    while (current != null) {
        known = fileSystem.findFileByNioFile(current)
        if (known != null) break
        current.fileName?.toString()?.let(missing::addFirst)
        current = current.parent
    }
    val existing = known?.let { packageOfExisting(project, it) } ?: return null
    val name = (listOf(existing).filter { it.isNotEmpty() } + missing).joinToString(".")
    return name.takeIf { it.isEmpty() || it.split('.').all(::isIdentifier) }
}

private fun packageOfExisting(project: Project, directory: VirtualFile): String? {
    val index = ProjectFileIndex.getInstance(project)
    val sourceRoot = index.getSourceRootForFile(directory) ?: return null
    val module = index.getModuleForFile(directory) ?: return null
    val prefix = ModuleRootManager.getInstance(module).contentEntries.asSequence()
        .flatMap { it.sourceFolders.asSequence() }
        .firstOrNull { it.file == sourceRoot }?.packagePrefix.orEmpty()
    val relative = VfsUtilCore.getRelativePath(directory, sourceRoot, '.') ?: return null
    return listOf(prefix, relative).filter { it.isNotEmpty() }.joinToString(".")
}

private fun isIdentifier(segment: String): Boolean =
    segment.isNotEmpty() && Character.isJavaIdentifierStart(segment[0]) && segment.all(Character::isJavaIdentifierPart)
