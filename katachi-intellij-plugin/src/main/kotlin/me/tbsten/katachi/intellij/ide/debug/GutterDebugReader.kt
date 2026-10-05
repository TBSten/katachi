package me.tbsten.katachi.intellij.ide.debug

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import me.tbsten.katachi.intellij.ide.gutter.GutterMenu
import me.tbsten.katachi.intellij.ide.sdkCall
import java.nio.file.Paths

/**
 * What [KatachiDebugBridge.describeGutter], `describeHighlights`, `clickGutter` and `chooseFromGutter` read: the ◆, the
 * annotations and the ◆ menu of a file open in the editor, as the `key=value` lines the Driver smoke asserts on.
 * It reads what the daemon drew (the markup model), not what the providers would compute, so it says what a user sees.
 *
 * A text value is on one line: a line break in it is written `\n`.
 *
 * ```kotlin
 * val lines = GutterDebugReader(project).describeGutter("/work/src/HealthService.kt").lines()
 * // "gutter=/work/src/HealthService.kt", "marks=1", "mark=7|menu|HealthService|katachi: domain.Service — allowed by class HealthService (ServiceRole.kt:80)"
 * ```
 */
internal class GutterDebugReader(private val project: Project) {
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
     * `file` for the banner over the file; fixes are `;` separated.
     */
    fun describeHighlights(path: String): String {
        val opened = openFile(path) ?: return "highlights=$path\nopen=false"
        // The editors the file-level highlights hang on are the EDT's: the daemon's own readers are called there.
        val lines = onEdtResult {
            ApplicationManager.getApplication().runReadAction<List<String>> {
                highlightsOf(opened).filter { it.description?.contains("katachi: ") == true }
                    .sortedWith(compareBy({ it.startOffset }, { it.description }))
                    .map { info ->
                        val range = if (info.isFileLevelAnnotation) "file" else rangeOf(opened.document, TextRange(info.startOffset, info.endOffset))
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

    private fun markersOf(document: Document): List<LineMarkerInfo<*>> =
        sdkCall("read the line markers") { DaemonCodeAnalyzerImpl.getLineMarkers(document, project) }.getOrDefault(emptyList())
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

    private fun highlightsOf(opened: Opened): List<HighlightInfo> {
        val daemon = DaemonCodeAnalyzer.getInstance(project) as? DaemonCodeAnalyzerImpl
        val psi: PsiFile? = sdkCall("find the PSI of ${opened.file.path}") { PsiManager.getInstance(project).findFile(opened.file) }.getOrNull()
        val ranged = sdkCall("read the highlights") { DaemonCodeAnalyzerImpl.getHighlights(opened.document, null, project) }.getOrDefault(emptyList())
        val banners = if (daemon != null && psi != null) {
            sdkCall("read the file-level highlights") { daemon.getFileLevelHighlights(project, psi) }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
        // A banner is the file-level list's alone: the ranged list is the markup model's, where a banner never sits.
        return ranged.filterNot { it.isFileLevelAnnotation } + banners
    }

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

    private fun rowsOf(group: DefaultActionGroup, nested: Boolean): List<String> = group.getChildren(null).flatMap { child ->
        val key = if (nested) "subitem" else "item"
        when {
            child is GutterMenu.RowAction -> listOf("$key=${child.row.text.oneLine()}|${child.row.trailing.orEmpty().oneLine()}|${if (child.row.enabled) "enabled" else "disabled"}")
            child is GutterMenu.SubMenu -> listOf("submenu=${child.templatePresentation.text.orEmpty().oneLine()}") + rowsOf(child, nested = true)
            child is Separator && child.text.isNullOrEmpty() -> listOf("separator")
            child is Separator -> listOf("section=${child.text.orEmpty().oneLine()}")
            else -> listOf("other=${child.javaClass.simpleName}")
        }
    }

    private fun allRows(group: DefaultActionGroup): List<GutterMenu.RowAction> = group.getChildren(null).flatMap { child: AnAction ->
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
