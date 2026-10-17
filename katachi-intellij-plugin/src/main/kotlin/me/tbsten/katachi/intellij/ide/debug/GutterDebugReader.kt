package me.tbsten.katachi.intellij.ide.debug

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx
import com.intellij.codeInsight.daemon.LineMarkerSettings
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.HyperlinkLabel
import com.intellij.util.ui.UIUtil
import me.tbsten.katachi.intellij.ide.gutter.HighlightingRefresher
import me.tbsten.katachi.intellij.ide.gutter.KatachiDeclarationLineMarkerProvider
import me.tbsten.katachi.intellij.ide.gutter.GutterMenu
import me.tbsten.katachi.intellij.ide.sdkCall
import java.awt.Component
import java.awt.Container
import java.awt.Window
import java.nio.file.Paths

/**
 * What [KatachiDebugBridge.describeGutter], `describeHighlights`, `clickGutter`, `chooseFromGutter` and the doors beside them
 * (`describeBanners`, `applyFix`, `chooseFromGutterLater`, `setGutterIcons`) read and do: the ◆, the annotations, the banners and
 * the ◆ menu of a file open in the editor, as the `key=value` lines the Driver smoke asserts on.
 * It reads what the daemon drew (the markup model), not what the providers would compute, so it says what a user sees.
 *
 * A text value is on one line: a line break in it is written `\n`.
 *
 * ```kotlin
 * val lines = GutterDebugReader(project).describeGutter("/work/src/HealthService.kt").lines()
 * // "gutter=/work/src/HealthService.kt", "marks=1", "mark=7|menu|HealthService|katachi: domain.Service — allowed by class HealthService (ServiceRole.kt:80)"
 * ```
 */
internal class GutterDebugReader(private val project: Project, private val onScreen: (Component) -> Boolean = { it.isShowing }) {
    /** The katachi ◆ of [path]: `gutter`, `marks`, then one `mark=<line>|menu or nomenu|<leaf>|<tooltip>` per ◆, top to bottom. */
    fun describeGutter(path: String): String {
        val opened = openFile(path) ?: return "gutter=$path\nopen=false"
        val marks = ApplicationManager.getApplication().runReadAction<List<String>> {
            markersOf(opened.document).map { info ->
                val line = opened.document.getLineNumber(info.startOffset) + 1
                val menu = if (info.navigationHandler is GutterMenu) "menu" else "nomenu"
                "mark=$line|$menu|${info.element?.text.orEmpty().oneLine()}|${tooltipOf(info).oneLine()}"
            }
        }
        return (listOf("gutter=$path", "open=true", "marks=${marks.size}") + marks).joinToString("\n")
    }

    /**
     * The katachi annotations of [path] (a description with `katachi: `): `highlights`, `count`, then one
     * `highlight=<severity>|<range>|<description>|<fixes>` each. The range is `line:col-line:col` (1-based, end exclusive) or
     * `file` for a banner over the file (see [highlightsOf]); fixes are `;` separated.
     */
    fun describeHighlights(path: String): String {
        val opened = openFile(path) ?: return "highlights=$path\nopen=false"
        // The editors the file-level highlights hang on are the EDT's: the daemon's own readers are called there.
        val lines = onEdtResult {
            ApplicationManager.getApplication().runReadAction<List<String>> {
                highlightsOf(opened).filter { it.description?.contains("katachi: ") == true }
                    .sortedWith(compareBy({ it.startOffset }, { it.description }))
                    .map { info ->
                        val range = if (isBanner(info)) "file" else rangeOf(opened.document, TextRange(info.startOffset, info.endOffset))
                        "highlight=${info.severity.name}|$range|${info.description.orEmpty().oneLine()}|${fixesOf(info).joinToString(";") { it.oneLine() }}"
                    }
            }
        }
        return (listOf("highlights=$path", "open=true", "count=${lines.size}") + lines).joinToString("\n")
    }

    /**
     * Opens the menu of the ◆ on [line] (1-based), reads its rows from the popup the user would get, and closes it. `menu`
     * (the heading), then `item=<text>|<right-hand text>|enabled or disabled`, `section=<title>`, `separator` and `submenu=<text>`
     * (its rows follow as `subitem`) in the order they are shown. `menu=none` when no ◆ is on the line.
     */
    fun clickGutter(path: String, line: Int): String {
        val menu = menuAt(path, line) ?: return "menu=none"
        val lines = mutableListOf<String>()
        onEdt {
            val context = contextOf(path)
            val popup = menu.createPopup(context)
            editorOf(path)?.let { sdkCall("show the katachi gutter menu") { popup.showInBestPositionFor(it) } }
            lines += "menu=${menu.title()}"
            rowsOf(menu.actionGroup(context), nested = false).forEach { lines += it }
            popup.cancel()
        }
        return lines.joinToString("\n")
    }

    /** Opens the menu of the ◆ on [line], writes the popup as a PNG to [out], and closes it (see [DebugScreenshots.ofPopup]); `saved=none` when no ◆ is there. */
    fun screenshotGutterMenu(path: String, line: Int, out: String): String {
        val menu = menuAt(path, line) ?: return "saved=none"
        var result = "saved=none"
        onEdt {
            val popup = menu.createPopup(contextOf(path))
            editorOf(path)?.let { sdkCall("show the katachi gutter menu") { popup.showInBestPositionFor(it) } }
            result = DebugScreenshots(project).ofPopup(popup, out)
            popup.cancel()
        }
        return result
    }

    /** Chooses the row of the ◆ menu on [line] whose text starts with [row], as a click on it would. `chosen=<text>` or `chosen=none`. */
    fun chooseFromGutter(path: String, line: Int, row: String): String {
        val menu = menuAt(path, line) ?: return "chosen=none"
        var chosen = "chosen=none"
        onEdt {
            val target = allRows(menu.actionGroup(contextOf(path))).firstOrNull { it.row.text.startsWith(row) && it.row.enabled }
            if (target != null) {
                chosen = "chosen=${target.row.text.oneLine()}"
                target.choose()
            }
        }
        return chosen
    }

    /**
     * The katachi banners on [path]: `banners`, `count`, then one `banner=<text>|<link>;<link>` each. A banner is read from the
     * part on screen that draws it (an [EditorNotificationPanel] over the editor, its text a `katachi: ` line), so it is what a
     * real IDE shows, which [describeHighlights] cannot list outside unit tests. A panel that is not on screen ([onScreen]: showing, unless a test says otherwise) is not counted.
     */
    fun describeBanners(path: String): String {
        val file = LocalFileSystem.getInstance().findFileByNioFile(Paths.get(path)) ?: return "banners=$path\ncount=0"
        val lines = onEdtResult {
            sdkCall("read the katachi banners") {
                bannerPanelsOf(file).filter { it.text.contains("katachi: ") && onScreen(it) }.map { bannerLine(it) }
            }.getOrDefault(emptyList())
        }
        return (listOf("banners=$path", "count=${lines.size}") + lines).joinToString("\n")
    }

    private fun bannerLine(panel: EditorNotificationPanel): String =
        "banner=${panel.text.oneLine()}|${UIUtil.findComponentsOfType(panel, HyperlinkLabel::class.java).joinToString(";") { it.text.oneLine() }}"

    /**
     * The panels over [file]'s editors: from each editor's component up to the part that holds this file's editor alone (the
     * parent just before one that also holds another editor, or a window), then every panel under it.
     */
    private fun bannerPanelsOf(file: VirtualFile): List<EditorNotificationPanel> {
        val manager = FileEditorManager.getInstance(project)
        val others = manager.allEditors.filter { it.file != file }.map { it.component }
        return manager.getAllEditors(file).flatMap { editor ->
            var top: Container = editor.component
            while (true) {
                val parent = top.parent ?: break
                if (parent is Window || others.any { parent.isAncestorOf(it) }) break
                top = parent
            }
            panelsUnder(top)
        }.distinct()
    }

    private fun panelsUnder(node: Component): List<EditorNotificationPanel> =
        (if (node is EditorNotificationPanel) listOf(node) else emptyList()) +
            ((node as? Container)?.components.orEmpty().flatMap { panelsUnder(it) })

    /**
     * Applies the first registered fix of the katachi annotation on [line] of [path] whose text starts with [prefix] and that is
     * available, as choosing it from the lightbulb would: `applied=<text>`, or `applied=none`. The fix runs later on the EDT
     * (a fix may open a modal dialog), as a command.
     */
    fun applyFix(path: String, line: Int, prefix: String): String {
        val opened = openFile(path) ?: return "applied=none"
        val editor = editorOf(path)
        val psiFile = onEdtResult {
            sdkCall("find the file of the katachi fix") {
                ApplicationManager.getApplication().runReadAction<com.intellij.psi.PsiFile?> { PsiManager.getInstance(project).findFile(opened.file) }
            }.getOrNull()
        } ?: return "applied=none"
        val fix = onEdtResult {
            sdkCall("find the katachi fix") {
                ApplicationManager.getApplication().runReadAction<IntentionAction?> {
                    var found: IntentionAction? = null
                    highlightsOf(opened)
                        .filter { it.description?.contains("katachi: ") == true && opened.document.getLineNumber(it.startOffset) + 1 == line }
                        .forEach { info ->
                            info.findRegisteredQuickFix<Any?> { descriptor, _ ->
                                val action = descriptor.action
                                if (found == null && action.text.startsWith(prefix) && action.isAvailable(project, editor, psiFile)) found = action
                                null
                            }
                        }
                    found
                }
            }.getOrNull()
        } ?: return "applied=none"
        ApplicationManager.getApplication().invokeLater {
            sdkCall("apply the katachi fix ${fix.text}") {
                CommandProcessor.getInstance().executeCommand(project, { fix.invoke(project, editor, psiFile) }, fix.text, null)
            }
        }
        return "applied=${fix.text.oneLine()}"
    }

    /** The selected text editor: `caret=<file name>|<line>` (1-based), or `caret=none`. For the smoke that checks where a fix took the user. */
    fun describeCaret(): String = onEdtResult {
        sdkCall("read the caret") {
            val editor = FileEditorManager.getInstance(project).selectedTextEditor
            val name = editor?.let { FileDocumentManager.getInstance().getFile(it.document)?.name }
            if (editor == null || name == null) "caret=none" else "caret=$name|${editor.caretModel.logicalPosition.line + 1}"
        }.getOrDefault("caret=none")
    }

    /**
     * [chooseFromGutter] with the choosing left for later on the EDT: a row that opens a modal dialog would hold the call that
     * chose it. `chosen=<text>` is answered once the choice is queued.
     */
    fun chooseFromGutterLater(path: String, line: Int, row: String): String {
        val menu = menuAt(path, line) ?: return "chosen=none"
        var chosen = "chosen=none"
        onEdt {
            val target = allRows(menu.actionGroup(contextOf(path))).firstOrNull { it.row.text.startsWith(row) && it.row.enabled }
            if (target != null) {
                chosen = "chosen=${target.row.text.oneLine()}"
                ApplicationManager.getApplication().invokeLater({ sdkCall("choose ${target.row.text} of the katachi gutter menu") { target.choose() } }, ModalityState.any())
            }
        }
        return chosen
    }

    /**
     * Turns the katachi switch of `Settings | Editor | General | Gutter Icons` on or off (the one both ◆ providers share) and draws the open
     * files again: `gutterIcons=<true or false>`.
     */
    fun setGutterIcons(enabled: Boolean): String {
        onEdt {
            sdkCall("switch the katachi gutter icons") { LineMarkerSettings.getSettings().setEnabled(KatachiDeclarationLineMarkerProvider(), enabled) }
            HighlightingRefresher.getInstance(project).redrawNow("the debug bridge switched the gutter icons ${if (enabled) "on" else "off"}")
        }
        return "gutterIcons=$enabled"
    }

    private class Opened(val file: VirtualFile, val document: Document)

    private fun openFile(path: String): Opened? {
        val file = LocalFileSystem.getInstance().findFileByNioFile(Paths.get(path)) ?: return null
        val document = FileDocumentManager.getInstance().getCachedDocument(file) ?: return null
        return Opened(file, document)
    }

    private fun editorOf(path: String): Editor? {
        val file = LocalFileSystem.getInstance().findFileByNioFile(Paths.get(path)) ?: return null
        return FileEditorManager.getInstance(project).getEditors(file).filterIsInstance<TextEditor>().firstOrNull()?.editor
    }

    private fun contextOf(path: String): DataContext =
        editorOf(path)?.let { DataManager.getInstance().getDataContext(it.contentComponent) } ?: DataContext.EMPTY_CONTEXT

    /**
     * The ◆ the daemon drew: the line markers behind the gutter icons of the document's markup model. Not
     * `DaemonCodeAnalyzerImpl.getLineMarkers`, which is `@ApiStatus.Internal` (verifyPlugin fails on it).
     */
    private fun markersOf(document: Document): List<LineMarkerInfo<*>> =
        sdkCall("read the line markers") {
            DocumentMarkupModel.forDocument(document, project, false)?.allHighlighters.orEmpty()
                .mapNotNull { (it.gutterIconRenderer as? LineMarkerInfo.LineMarkerGutterIconRenderer<*>)?.lineMarkerInfo }
        }.getOrDefault(emptyList())
            .filter { it.lineMarkerTooltip?.startsWith("katachi:") == true }
            .sortedBy { it.startOffset }

    /** The raw text of the tooltip: the accessible name, because the tooltip itself is the HTML the gutter draws. */
    private fun tooltipOf(info: LineMarkerInfo<*>): String = info.createGutterRenderer()?.accessibleName ?: info.lineMarkerTooltip.orEmpty()

    private fun menuAt(path: String, line: Int): GutterMenu? {
        val opened = openFile(path) ?: return null
        return ApplicationManager.getApplication().runReadAction<GutterMenu?> {
            markersOf(opened.document).firstOrNull { opened.document.getLineNumber(it.startOffset) + 1 == line }?.navigationHandler as? GutterMenu
        }
    }

    /**
     * The ranged highlights of the document: public API, and the very list the editor draws the underlines from. A banner
     * over the whole file (`fileLevel()`) is in it in unit tests only: `DaemonCodeAnalyzerImpl.getFileLevelHighlights`, the
     * platform's own list of them, asserts unit-test mode, so a real IDE lists no banner here; [describeBanners] reads the
     * banner from the component it is drawn as instead.
     */
    private fun highlightsOf(opened: Opened): List<HighlightInfo> = sdkCall("read the highlights") {
        val found = mutableListOf<HighlightInfo>()
        DaemonCodeAnalyzerEx.processHighlights(opened.document, project, null, 0, opened.document.textLength) { found += it; true }
        found
    }.getOrDefault(emptyList())

    /** Whether [info] is a banner. `HighlightInfo.isFileLevelAnnotation` is `@ApiStatus.Internal` (verifyPlugin fails on it), so it is called by name. */
    private fun isBanner(info: HighlightInfo): Boolean =
        sdkCall("ask whether a highlight is a banner") { HighlightInfo::class.java.getMethod("isFileLevelAnnotation").invoke(info) as Boolean }.getOrDefault(false)

    private fun fixesOf(info: HighlightInfo): List<String> {
        val found = mutableListOf<String>()
        info.findRegisteredQuickFix<Any?> { descriptor, _ ->
            found += descriptor.action.text
            null
        }
        return found
    }

    private fun rangeOf(document: Document, range: TextRange): String {
        fun at(offset: Int) = "${document.getLineNumber(offset) + 1}:${offset - document.getLineStartOffset(document.getLineNumber(offset)) + 1}"
        return "${at(range.startOffset)}-${at(range.endOffset)}"
    }

    private fun rowsOf(group: DefaultActionGroup, nested: Boolean): List<String> = group.getChildActionsOrStubs().flatMap { child ->
        val key = if (nested) "subitem" else "item"
        when {
            child is GutterMenu.RowAction -> listOf("$key=${child.row.text.oneLine()}|${child.row.trailing.orEmpty().oneLine()}|${if (child.row.enabled) "enabled" else "disabled"}")
            child is GutterMenu.SubMenu -> listOf("submenu=${child.templatePresentation.text.orEmpty().oneLine()}") + rowsOf(child, nested = true)
            child is Separator && child.text.isNullOrEmpty() -> listOf("separator")
            child is Separator -> listOf("section=${child.text.orEmpty().oneLine()}")
            else -> listOf("other=${child.javaClass.simpleName}")
        }
    }

    private fun allRows(group: DefaultActionGroup): List<GutterMenu.RowAction> = group.getChildActionsOrStubs().flatMap { child: AnAction ->
        when (child) {
            is GutterMenu.RowAction -> listOf(child)
            is DefaultActionGroup -> allRows(child)
            else -> emptyList()
        }
    }

    private fun onEdt(block: () -> Unit) = ApplicationManager.getApplication().invokeAndWait(block)

    /** [block] on the EDT, and what it returns (or throws) here. */
    private fun <T> onEdtResult(block: () -> T): T {
        var result: Result<T>? = null
        ApplicationManager.getApplication().invokeAndWait { result = runCatching(block) }
        return (result ?: throw IllegalStateException("the EDT did not run the block")).getOrThrow()
    }

    private fun String.oneLine(): String = replace("\n", "\\n")
}
