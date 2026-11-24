package me.tbsten.katachi.intellij.ide.authoring.livetemplate

import com.intellij.codeInsight.template.ExpressionContext
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.impl.LoadTextUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import me.tbsten.katachi.intellij.data.definition.CaptureValues
import me.tbsten.katachi.intellij.ide.authoring.AuthoringPorts
import me.tbsten.katachi.intellij.ide.definition.ResolvedSite
import me.tbsten.katachi.intellij.model.LayoutEntryModel
import me.tbsten.katachi.intellij.model.answer
import me.tbsten.katachi.intellij.presentation.authoring.DraftRole
import me.tbsten.katachi.intellij.presentation.authoring.FileContentDraft
import me.tbsten.katachi.intellij.presentation.authoring.FileContentDrafts
import me.tbsten.katachi.intellij.presentation.authoring.TemplateDrafts
import me.tbsten.katachi.intellij.presentation.authoring.draftRoleOf
import me.tbsten.katachi.intellij.presentation.gutter.RoleFileCount
import org.jetbrains.kotlin.psi.KtFile

/** What `katachitemplate` fills: the body of the template, and the names of the captures that body uses, in the order they appear. */
internal data class TemplateDraftText(val body: String, val captureNames: List<String>)

/**
 * What the live templates fill from the definition the plugin has loaded (ide-live-template-from-definition): the role of the
 * open file and the files the role holds. Every answer is `null` when the definition is not loaded, the open file declares no
 * role or the role holds no file, and the template then expands as the fixed text of katachi.xml.
 *
 * Read under the read lock a macro runs in. The files read are the ones the definition listed when it was read, at most
 * [FILES_READ] of them: a Tab on a role of thousands of files stays as quick as one of a few.
 */
internal object KatachiDraftSource {
    const val FILES_READ: Int = 40

    /** The files an entry is read from for `katachitemplate`: the shortest of the first few is the one that is easiest to take for a template. */
    private const val TEMPLATE_CANDIDATES: Int = 10

    /** A file of this many characters is not read for a template. */
    private const val TEMPLATE_FILE_MAX: Int = 20_000

    fun fileContent(context: ExpressionContext): FileContentDraft? {
        val found = roleOf(context) ?: return null
        val psi = PsiManager.getInstance(context.project)
        val samples = filesOf(found).mapNotNull { KatachiSampleFiles.of(psi.findFile(it.file) as? KtFile ?: return@mapNotNull null) }
        return FileContentDrafts.of(samples)
    }

    fun template(context: ExpressionContext): TemplateDraftText? {
        val found = roleOf(context) ?: return null
        val candidates = filesOf(found).take(TEMPLATE_CANDIDATES).mapNotNull { file -> textOf(file.file)?.takeIf { it.isNotBlank() }?.let { file to it } }
        val (chosen, text) = candidates.minByOrNull { it.second.length } ?: return null
        val captures = CaptureValues.of(chosen.entry, chosen.path)
        val body = TemplateDrafts.bodyOf(text, captures) ?: return null
        return TemplateDraftText(body, captures.map { it.name }.filter { "\${$it}" in body })
    }

    private class Found(val draft: DraftRole, val root: java.nio.file.Path, val ports: LiveTemplatePorts)

    private fun roleOf(context: ExpressionContext): Found? {
        val project = context.project
        val editor = context.editor ?: return null
        val file = PsiDocumentManager.getInstance(project).getPsiFile(editor.document)?.virtualFile ?: return null
        val service = project.service<AuthoringPorts>().definition() ?: return null
        service.ensureLoaded()
        val snapshots = service.state.value.answer?.definitions ?: return null
        val line = editor.document.getLineNumber(context.startOffset.coerceIn(0, editor.document.textLength)) + 1
        val draft = draftRoleOf(file.name, line, snapshots) { snapshot, site ->
            (service.resolve(snapshot.module.id, site) as? ResolvedSite.Found)?.file == file
        } ?: return null
        val root = draft.snapshot.definition?.projectRoot ?: return null
        return Found(draft, root, project.service<LiveTemplatePorts>())
    }

    /** A file of the role: [path] is how the definition lists it, relative to the project root. */
    private class RoleFile(val entry: LayoutEntryModel, val path: String, val file: VirtualFile)

    /** The files the file entries of the role listed that are still there, `FILES_READ` at most. */
    private fun filesOf(found: Found): List<RoleFile> =
        RoleFileCount.holders(found.draft.role.layout)
            .flatMap { entry -> entry.matchedFiles.map { entry to it } }
            .distinctBy { it.second }
            .asSequence()
            .mapNotNull { (entry, path) -> found.ports.file(found.root.resolve(path))?.takeIf { it.isValid && !it.isDirectory }?.let { RoleFile(entry, path, it) } }
            .take(FILES_READ)
            .toList()

    private fun textOf(file: VirtualFile): String? =
        file.takeIf { it.length <= TEMPLATE_FILE_MAX && !it.fileType.isBinary }?.let { LoadTextUtil.loadText(it).toString() }
}
