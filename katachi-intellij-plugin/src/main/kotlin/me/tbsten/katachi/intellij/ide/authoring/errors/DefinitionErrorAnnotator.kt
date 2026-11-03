package me.tbsten.katachi.intellij.ide.authoring.errors

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiFile
import com.intellij.xml.util.XmlStringUtil
import me.tbsten.katachi.intellij.ide.authoring.BundleAuthoringStrings
import me.tbsten.katachi.intellij.ide.definition.ResolvedSite
import me.tbsten.katachi.intellij.ide.gutter.GutterPorts
import me.tbsten.katachi.intellij.ide.gutter.HighlightingRefresher
import me.tbsten.katachi.intellij.ide.gutter.annotator.AnnotationRanges
import me.tbsten.katachi.intellij.ide.sdkCall
import me.tbsten.katachi.intellij.model.DeclarationSiteRef
import me.tbsten.katachi.intellij.model.Freshness
import me.tbsten.katachi.intellij.model.ModuleId
import me.tbsten.katachi.intellij.model.answer
import me.tbsten.katachi.intellij.presentation.authoring.DefinitionErrorTargets
import me.tbsten.katachi.intellij.presentation.authoring.DefinitionMarkSource
import me.tbsten.katachi.intellij.presentation.gutter.AnnotationSeverity

internal class DefinitionErrorAnnotator(
    private val portsOf: (Project) -> GutterPorts,
    private val showMessage: (Project, String) -> Unit = ::showErrorDialog,
) : ExternalAnnotator<DefinitionErrorInput, DefinitionErrorOutput>(),
    DumbAware {
    constructor() : this(GutterPorts::of)

    override fun collectInformation(file: PsiFile, editor: Editor, hasErrors: Boolean): DefinitionErrorInput? =
        sdkCall("collect what the katachi definition error annotator needs of ${file.name}") { collect(file) }.getOrNull()

    private fun collect(file: PsiFile): DefinitionErrorInput? {
        // The plugin's own injection puts Kotlin in a definition file's `.template { }`: that fragment is not a definition file.
        if (InjectedLanguageManager.getInstance(file.project).isInjectedFragment(file)) return null
        return DefinitionErrorInput(file, file.viewProvider.virtualFile, portsOf(file.project))
    }

    override fun doAnnotate(collectedInfo: DefinitionErrorInput?): DefinitionErrorOutput? {
        val input = collectedInfo ?: return null
        return sdkCall("work out the katachi definition errors of ${input.virtualFile.name}") { plan(input) }.getOrNull()
    }

    private fun plan(input: DefinitionErrorInput): DefinitionErrorOutput? {
        val ports = input.ports
        if (!ports.hasDefinitionModule()) return null
        val definitions = ports.definitions() ?: return null
        // The daemon does not run again when the errors change behind its back: the refresher does that, so it follows this service too.
        HighlightingRefresher.getInstance(input.file.project).follow(definitions, ports.violations())
        val errors = definitions.definitionErrors.value
        val snapshots = definitions.definitions
        if (errors.errors.isEmpty() && snapshots.none { snapshot -> snapshot.definition?.problems.orEmpty().any(DefinitionErrorTargets::isDrawn) }) return null
        val stale = (definitions.state.value.answer?.freshness ?: Freshness.Fresh) is Freshness.Stale
        // Finding a site can walk the test sources (`resolve`): it runs before the read action, so a write (typing) never waits for the walk.
        val sites = HashMap<Pair<ModuleId, DeclarationSiteRef>, Int?>()
        fun lineHere(module: ModuleId, site: DeclarationSiteRef): Int? = sites.getOrPut(module to site) {
            (definitions.resolve(module, site) as? ResolvedSite.Found)?.takeIf { it.file == input.virtualFile }?.line
        }
        errors.errors.forEach { error -> error.site?.let { lineHere(error.module, it) } }
        snapshots.forEach { snapshot ->
            snapshot.definition?.problems.orEmpty().filter(DefinitionErrorTargets::isDrawn).forEach { problem ->
                DefinitionErrorTargets.siteOfProblem(problem, snapshot)?.let { lineHere(snapshot.module.id, it) }
            }
        }
        val planned = ReadAction.nonBlocking<List<PlannedDefinitionMark>> {
            val document = input.file.viewProvider.document ?: return@nonBlocking emptyList()
            DefinitionErrorTargets.marksOf(errors, snapshots, stale, { module, site -> sites[module to site] }, document.lineCount, BundleAuthoringStrings).mapNotNull { mark ->
                val range = AnnotationRanges.lineRange(document, mark.line) ?: return@mapNotNull null
                PlannedDefinitionMark(
                    range = range,
                    severity = mark.severity,
                    hover = mark.hover,
                    source = mark.source,
                    fullMessage = mark.errorIndex?.let { errors.errors[it].message },
                )
            }
        }.executeSynchronously()
        if (planned.isEmpty()) return null
        return DefinitionErrorOutput(definitions, planned)
    }

    override fun apply(file: PsiFile, annotationResult: DefinitionErrorOutput?, holder: AnnotationHolder) {
        val output = annotationResult ?: return
        sdkCall("draw the katachi definition errors of ${file.name}") {
            for (mark in output.marks) {
                // The builder is immutable: every call returns the one to go on with.
                var builder = holder.newAnnotation(severityOf(mark.severity), mark.hover).tooltip(tooltipOf(mark.hover)).range(mark.range)
                if (mark.source == DefinitionMarkSource.Error) {
                    builder = builder.withFix(ReloadDefinitionFix(output.definitions))
                    mark.fullMessage?.let { builder = builder.withFix(ShowWholeMessageFix(it, showMessage)) }
                }
                builder.create()
            }
        }
    }

    private fun severityOf(severity: AnnotationSeverity): HighlightSeverity = when (severity) {
        AnnotationSeverity.Error -> HighlightSeverity.ERROR
        AnnotationSeverity.Warning -> HighlightSeverity.WARNING
        AnnotationSeverity.WeakWarning -> HighlightSeverity.WEAK_WARNING
        AnnotationSeverity.Information -> HighlightSeverity.INFORMATION
    }

    // The hover is HTML: a message's `<T>` and `&` must show, and its lines stay lines.
    private fun tooltipOf(hover: String): String =
        XmlStringUtil.wrapInHtml(hover.lines().joinToString("<br>") { StringUtil.escapeXmlEntities(it) })
}
