package me.tbsten.katachi.intellij.ide.authoring.codevision

import com.intellij.codeInsight.codeVision.CodeVisionAnchorKind
import com.intellij.codeInsight.codeVision.CodeVisionEntry
import com.intellij.codeInsight.codeVision.CodeVisionRelativeOrdering
import com.intellij.codeInsight.codeVision.ui.model.TextCodeVisionEntry
import com.intellij.codeInsight.hints.codeVision.DaemonBoundCodeVisionProvider
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import me.tbsten.katachi.intellij.ide.KatachiBundle
import me.tbsten.katachi.intellij.ide.authoring.BundleAuthoringStrings
import me.tbsten.katachi.intellij.ide.gutter.GutterNavigation
import me.tbsten.katachi.intellij.ide.gutter.GutterPorts
import me.tbsten.katachi.intellij.ide.sdkCall
import me.tbsten.katachi.intellij.presentation.authoring.EntryFilesInput
import me.tbsten.katachi.intellij.presentation.authoring.EntryFilesPopupModel
import me.tbsten.katachi.intellij.presentation.authoring.EntryFilesPopups
import org.jetbrains.kotlin.psi.KtFile
import java.nio.file.Path

internal class EntryFilesEntry(text: String, providerId: String, val input: EntryFilesInput) :
    TextCodeVisionEntry(text, providerId, null, text, text, emptyList())

internal class EntryFilesCodeVisionProvider(
    private val portsOf: (Project) -> GutterPorts,
    private val background: (() -> Unit) -> Unit = { work -> ApplicationManager.getApplication().executeOnPooledThread(work) },
    private val show: (Editor, TextRange, EntryFilesPopupModel, (Path) -> Unit) -> Unit = ::showEntryFilesPopup,
) : DaemonBoundCodeVisionProvider {
    constructor() : this(GutterPorts::of)

    override val name: String get() = KatachiBundle.message("authoring.codevision.name")

    override val relativeOrderings: List<CodeVisionRelativeOrdering> get() = emptyList()

    override val defaultAnchor: CodeVisionAnchorKind get() = CodeVisionAnchorKind.Right

    override val id: String get() = ID

    override val groupId: String get() = KatachiCodeVisionGroup.GROUP_ID

    override fun computeForEditor(editor: Editor, file: PsiFile): List<Pair<TextRange, CodeVisionEntry>> {
        val ktFile = file as? KtFile ?: return emptyList()
        val lenses = sdkCall("compute the Code Vision of ${file.name}") { EntryFilesLenses.of(ktFile, portsOf(file.project)) }.getOrNull().orEmpty()
        return lenses.map { it.range to EntryFilesEntry(it.text, ID, it.input) }
    }

    override fun handleClick(editor: Editor, textRange: TextRange, entry: CodeVisionEntry) {
        val input = (entry as? EntryFilesEntry)?.input ?: return
        val project = editor.project ?: return
        val navigation = GutterNavigation(project, portsOf(project).notify)
        background {
            val popup = sdkCall("read the files behind the Code Vision") { EntryFilesPopups.of(input, BundleAuthoringStrings) { directory, limit -> DirectoryListing.namesIn(input.projectRoot, directory, limit) } }.getOrNull()
            if (popup != null) ApplicationManager.getApplication().invokeLater { show(editor, textRange, popup) { navigation.openFile(it) } }
        }
    }

    companion object {
        const val ID: String = "katachi.entryFiles"
    }
}
