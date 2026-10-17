package me.tbsten.katachi.intellij.ide.debug

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.ui.ClientProperty
import com.intellij.ui.HyperlinkLabel
import com.intellij.util.ui.UIUtil
import me.tbsten.katachi.intellij.ide.authoring.AuthoringPorts
import me.tbsten.katachi.intellij.ide.authoring.codevision.EntryFilesLenses
import me.tbsten.katachi.intellij.ide.authoring.completion.CompletionSite
import me.tbsten.katachi.intellij.ide.authoring.completion.completionCandidatesFor
import me.tbsten.katachi.intellij.ide.authoring.completion.completionSite
import me.tbsten.katachi.intellij.ide.authoring.errors.DefinitionErrorNotificationProvider
import me.tbsten.katachi.intellij.ide.authoring.gutter.DefinitionGutterMenu
import me.tbsten.katachi.intellij.ide.gutter.GutterMenu
import me.tbsten.katachi.intellij.ide.gutter.GutterPorts
import me.tbsten.katachi.intellij.ide.sdkCall
import me.tbsten.katachi.intellij.model.answer
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import java.awt.Component
import java.nio.file.Paths

/**
 * What the three doors of the debug bridge for writing a definition read (ide-authoring design 5, kept apart from [KatachiDebugBridge]
 * and [GutterDebugReader] so that the quality lane's doors are not touched): [describeDefinitionErrors], [describeCompletion] and
 * [describeDefinitionGutter]. Each answers `key=value` lines, a text value on one line (a line break is `\n`), as the Driver smoke
 * asserts on them.
 *
 * ```kotlin
 * val lines = AuthoringDebugReader(project).describeDefinitionErrors("/work/src/test/kotlin/RepositoryRole.kt").lines()
 * // "definitionErrors=/work/...", "errors=1", "stale=false", "error=:architecture-test|EntryPoint|java.lang.IllegalStateException|RepositoryRole.kt:12|..."
 * ```
 */
internal class AuthoringDebugReader(
    private val project: Project,
    private val onScreen: (Component) -> Boolean = { it.isShowing },
    /** What the ◆ and the Code Vision read of the project; a test swaps it for fakes. */
    private val portsOf: (Project) -> GutterPorts = GutterPorts::of,
) {
    /**
     * The mistakes katachi found in the definition and where the IDE put them in [path]: `definitionErrors`, `errors`, `stale`, one
     * `error=<module>|<phase>|<exception>|<File.kt:line or none>|<first line>` each, then `highlights` with one `highlight` each
     * (the underlines of the file, as [GutterDebugReader.describeHighlights] reads them) and `banners` with one `banner=<text>|<links>`
     * each, read from the parts on screen. A banner is one of the definition error notification's (marked by
     * [DefinitionErrorNotificationProvider.BANNER_KEY]), not any katachi panel.
     */
    fun describeDefinitionErrors(path: String): String {
        val errors = project.service<AuthoringPorts>().definition()?.definitionErrors?.value
        val lines = mutableListOf("definitionErrors=$path", "errors=${errors?.errors?.size ?: 0}", "stale=${errors?.stale ?: false}")
        errors?.errors?.forEach { error ->
            val site = error.site?.let { "${it.fileName}:${it.line}" } ?: "none"
            lines += "error=${error.module.gradlePath}|${error.phase}|${error.exceptionClassName}|$site|${error.message.lineSequence().firstOrNull().orEmpty().oneLine()}"
        }
        val highlights = GutterDebugReader(project, onScreen).describeHighlights(path).lines().filter { it.startsWith("highlight=") }
        lines += "highlights=${highlights.size}"
        lines += highlights
        val banners = bannersOf(path)
        lines += "banners=${banners.size}"
        lines += banners
        return lines.joinToString("\n")
    }

    /**
     * What a completion at [offset] (0-based, in the document) of [path] would offer: `completion=<path>|<offset>`, `site` (`CaptureValue(line)`,
     * `TemplateSpec`, `ArgName(spec)` or `none`), `candidates` and one `candidate=<text>|<type text>|<tail text>` each, in the order the
     * contributor lists them. It runs the contributor's own computation, not a completion popup (which is the platform's), so
     * it says what the names are, and whether the place is one.
     */
    fun describeCompletion(path: String, offset: Int): String {
        val header = "completion=$path|$offset"
        val file = psiFileOf(path) ?: return "$header\nopen=false"
        val lines = ApplicationManager.getApplication().runReadAction<List<String>> {
            sdkCall("read the completion at $offset of $path") {
                val string = PsiTreeUtil.getParentOfType(file.findElementAt(offset), KtStringTemplateExpression::class.java, false)
                val site = string?.completionSite()
                val snapshots = project.service<AuthoringPorts>().definition()?.state?.value?.answer?.definitions.orEmpty()
                val candidates = site?.let { completionCandidatesFor(it, file, snapshots) }.orEmpty()
                listOf("site=${siteText(site)}", "candidates=${candidates.size}") +
                    candidates.map { "candidate=${it.text.oneLine()}|${it.typeText.oneLine()}|${it.tailText.orEmpty().oneLine()}" }
            }.getOrElse { listOf("site=unreadable: ${it.message.orEmpty().oneLine()}") }
        }
        return (listOf(header, "open=true") + lines).joinToString("\n")
    }

    /**
     * The definition side of [path], a definition file open in the editor: `definitionGutter`, `open`, `marks` and one
     * `mark=<line>|<leaf>|<tooltip>` for each ◆ the daemon drew, each followed by the rows its menu lists (`item=<text>|<right side>|enabled or
     * disabled`, `section`, `separator`, `submenu` with `subitem`), then `lenses` and one `lens=<line>|<text>` for each Code Vision.
     * The ◆ are read from what the daemon drew; the Code Vision from the provider's own computation (the platform draws those as inlays
     * a smoke cannot read).
     */
    fun describeDefinitionGutter(path: String): String {
        val file = LocalFileSystem.getInstance().findFileByNioFile(Paths.get(path))
        val document = file?.let { FileDocumentManager.getInstance().getCachedDocument(it) }
        if (file == null || document == null) return "definitionGutter=$path\nopen=false"
        // The markers, the PSI leaf they stand on and the tooltips are read in one read action (a Driver call runs off the EDT); the menus are built on the EDT afterwards.
        val marks = ApplicationManager.getApplication().runReadAction<List<DrawnMark>> {
            sdkCall("read the definition line markers") {
                definitionMarkersOf(document).map { info ->
                    val line = document.getLineNumber(info.startOffset) + 1
                    val tooltip = info.createGutterRenderer()?.accessibleName ?: info.lineMarkerTooltip.orEmpty()
                    DrawnMark("mark=$line|${info.element?.text.orEmpty().oneLine()}|${tooltip.oneLine()}", info.navigationHandler as? DefinitionGutterMenu)
                }
            }.getOrDefault(emptyList())
        }
        val markLines = marks.flatMap { listOf(it.line) + menuRows(it.menu) }
        val lenses = lensesOf(file, document)
        return (listOf("definitionGutter=$path", "open=true", "marks=${marks.size}") + markLines + listOf("lenses=${lenses.size}") + lenses).joinToString("\n")
    }

    private fun bannersOf(path: String): List<String> {
        val file = LocalFileSystem.getInstance().findFileByNioFile(Paths.get(path)) ?: return emptyList()
        val panels = onEdtResult {
            sdkCall("read the katachi definition banners") {
                GutterDebugReader(project, onScreen).bannerPanelsOf(file).filter { ClientProperty.get(it, DefinitionErrorNotificationProvider.BANNER_KEY) == true && onScreen(it) }.map { panel ->
                    "banner=${panel.text.orEmpty().oneLine()}|${UIUtil.findComponentsOfType(panel, HyperlinkLabel::class.java).joinToString(";") { it.text.oneLine() }}"
                }
            }.getOrDefault(emptyList())
        }
        return panels
    }

    private fun psiFileOf(path: String): KtFile? = ApplicationManager.getApplication().runReadAction<KtFile?> {
        val file = LocalFileSystem.getInstance().findFileByNioFile(Paths.get(path)) ?: return@runReadAction null
        PsiManager.getInstance(project).findFile(file) as? KtFile
    }

    /** A ◆ as the daemon drew it: its `mark=` line, read under the read action, and the menu its click opens. */
    private class DrawnMark(val line: String, val menu: DefinitionGutterMenu?)

    private fun definitionMarkersOf(document: Document): List<LineMarkerInfo<*>> =
        DocumentMarkupModel.forDocument(document, project, false)?.allHighlighters.orEmpty()
            .mapNotNull { (it.gutterIconRenderer as? LineMarkerInfo.LineMarkerGutterIconRenderer<*>)?.lineMarkerInfo }
            .filter { it.navigationHandler is DefinitionGutterMenu }
            .sortedBy { it.startOffset }

    private fun menuRows(menu: DefinitionGutterMenu?): List<String> {
        menu ?: return emptyList()
        return onEdtResult { sdkCall("read the rows of a definition ◆") { rowsOf(menu.actionGroup(DataContext.EMPTY_CONTEXT), nested = false) }.getOrDefault(emptyList()) }
    }

    private fun lensesOf(file: VirtualFile, document: Document): List<String> {
        val ktFile = ApplicationManager.getApplication().runReadAction<KtFile?> { PsiManager.getInstance(project).findFile(file) as? KtFile } ?: return emptyList()
        val lenses = ApplicationManager.getApplication().runReadAction<List<String>> {
            sdkCall("compute the Code Vision of ${file.name}") {
                EntryFilesLenses.of(ktFile, portsOf(project)).map { "lens=${document.getLineNumber(it.range.startOffset) + 1}|${it.text.oneLine()}" }
            }.getOrDefault(emptyList())
        }
        return lenses
    }

    private fun rowsOf(group: DefaultActionGroup, nested: Boolean): List<String> = group.getChildActionsOrStubs().flatMap { child: AnAction ->
        val key = if (nested) "subitem" else "item"
        when {
            child is DefinitionGutterMenu.RowAction ->
                listOf("$key=${child.row.text.oneLine()}|${child.row.trailing.orEmpty().oneLine()}|${if (child.row.enabled) "enabled" else "disabled"}")
            child is GutterMenu.SubMenu -> listOf("submenu=${child.templatePresentation.text.orEmpty().oneLine()}") + rowsOf(child, nested = true)
            child is Separator && child.text.isNullOrEmpty() -> listOf("separator")
            child is Separator -> listOf("section=${child.text.orEmpty().oneLine()}")
            else -> listOf("other=${child.javaClass.simpleName}")
        }
    }

    private fun siteText(site: CompletionSite?): String = when (site) {
        null -> "none"
        is CompletionSite.CaptureValue -> "CaptureValue(${site.templateLine})"
        CompletionSite.TemplateSpec -> "TemplateSpec"
        is CompletionSite.ArgName -> "ArgName(${site.spec})"
    }

    private fun <T> onEdtResult(block: () -> T): T {
        var result: Result<T>? = null
        ApplicationManager.getApplication().invokeAndWait { result = runCatching(block) }
        return (result ?: throw IllegalStateException("the EDT did not run the block")).getOrThrow()
    }

    private fun String.oneLine(): String = replace("\n", "\\n")
}
