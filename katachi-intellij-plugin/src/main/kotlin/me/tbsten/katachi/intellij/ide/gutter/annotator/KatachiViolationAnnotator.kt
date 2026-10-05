package me.tbsten.katachi.intellij.ide.gutter.annotator

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiFile
import com.intellij.xml.util.XmlStringUtil
import me.tbsten.katachi.intellij.ide.definition.DefinitionModelService
import me.tbsten.katachi.intellij.ide.definition.ResolvedSite
import me.tbsten.katachi.intellij.ide.gutter.GutterPorts
import me.tbsten.katachi.intellij.ide.gutter.HighlightingRefresher
import me.tbsten.katachi.intellij.ide.sdkCall
import me.tbsten.katachi.intellij.model.ViolationModel
import me.tbsten.katachi.intellij.model.ViolationsState
import me.tbsten.katachi.intellij.model.answer
import me.tbsten.katachi.intellij.presentation.gutter.AnnotationSeverity
import me.tbsten.katachi.intellij.presentation.gutter.AnnotationTarget
import me.tbsten.katachi.intellij.presentation.gutter.GutterFreshness
import me.tbsten.katachi.intellij.presentation.gutter.RuleSite
import me.tbsten.katachi.intellij.presentation.gutter.ViolationAnnotations
import me.tbsten.katachi.intellij.presentation.gutter.ViolationBinding

/**
 * Draws the violations of the last check in the source file (design 2.5): `UnsatisfiedFileConstraint` under the name of
 * the declaration it rejected, or on its line, or in a banner over the editor when it names neither; `UncheckedFile` in
 * the banner. `UnexpectedFile` is not drawn: a file no role holds is the editor notification's.
 *
 * The answers come from [GutterPorts.violations] (already read into memory), found in [doAnnotate] on the background
 * thread; [apply] on the EDT only creates the annotations. The 3-argument [collectInformation] is overridden so a file
 * with a compile error is annotated too (U-12). A check that may be out of date draws one step weaker. Off with
 * `Show katachi violations in the editor`. Works in dumb mode: the declarations are read from the PSI alone.
 *
 * ```kotlin
 * // plugin.xml
 * // <externalAnnotator language="kotlin" implementationClass="...annotator.KatachiViolationAnnotator"/>
 * ```
 */
internal class KatachiViolationAnnotator(private val portsOf: (Project) -> GutterPorts) :
    ExternalAnnotator<AnnotatorInput, AnnotatorOutput>(),
    DumbAware {
    constructor() : this(GutterPorts::of)

    override fun collectInformation(file: PsiFile, editor: Editor, hasErrors: Boolean): AnnotatorInput? =
        sdkCall("collect what the katachi violation annotator needs of ${file.name}") { collect(file) }.getOrNull()

    private fun collect(file: PsiFile): AnnotatorInput? {
        // The plugin's own injection puts Kotlin in a definition file's `.template { }`: that fragment is not a source file.
        if (InjectedLanguageManager.getInstance(file.project).isInjectedFragment(file)) return null
        val virtualFile = file.viewProvider.virtualFile
        val ports = portsOf(file.project)
        if (!ports.settings().showViolationsInEditor) return null
        return AnnotatorInput(file, virtualFile, ports)
    }

    override fun doAnnotate(collectedInfo: AnnotatorInput?): AnnotatorOutput? {
        val input = collectedInfo ?: return null
        return sdkCall("work out the katachi violations of ${input.virtualFile.name}") { plan(input) }.getOrNull()
    }

    private fun plan(input: AnnotatorInput): AnnotatorOutput? {
        val ports = input.ports
        if (!ports.hasDefinitionModule()) return null
        val violations = ports.violations() ?: return null
        val definitions = ports.definitions()
        // A definition written by a katachi older than the IDE reads: the gutter draws nothing (design 2.6), nor do the annotations.
        if (definitions != null && GutterFreshness.isTooOld(definitions.state.value)) return null
        // The daemon does not run again when the check ends behind its back: the refresher does that. A file with no ◆ (a role without a source file) still has violations.
        definitions?.let { HighlightingRefresher.getInstance(input.file.project).follow(it, violations) }
        val state = violations.state.value
        val answer = state.answer ?: return null
        // A SARIF that could not be read keeps the answer before it, which is as old as the unreadable file says.
        val stale = answer.stale || state is ViolationsState.Unreadable
        val bound = ViolationBinding.bindSourceSide(violations.forFile(input.virtualFile)).filter { it.target != AnnotationTarget.Skipped }
        if (bound.isEmpty()) return null
        val sites = HashMap<RuleSite, RuleTarget?>()
        // Finding the rule can walk the test sources (`resolve`): it runs before the read action, so a write (typing) never waits for the walk.
        val rules = bound.map { ruleOf(it.violation, definitions, sites) }
        // doAnnotate is not given a read action (it is meant for slow tools): the PSI and the Document are read under our own, a cancellable one
        // (a write action cancels it and it starts again), and it holds nothing but those reads.
        val planned = ReadAction.nonBlocking<List<PlannedAnnotation>> {
            val document = input.file.viewProvider.document
            bound.mapIndexed { index, (violation, target) ->
                PlannedAnnotation(
                    range = rangeOf(input.file, document, target),
                    severity = ViolationAnnotations.severityOf(violation.level, stale),
                    hover = ViolationAnnotations.hoverOf(violation, target, stale),
                    module = violation.module,
                    rule = rules[index],
                )
            }
        }.executeSynchronously()
        return AnnotatorOutput(input.virtualFile, ports, planned)
    }

    /** Under the name of the declaration if the file still has it, else on the line the check saw if it has text, else `null`: the banner. */
    private fun rangeOf(file: PsiFile, document: Document?, target: AnnotationTarget): TextRange? {
        if (document == null) return null
        return when (target) {
            is AnnotationTarget.AtDeclaration ->
                AnnotationRanges.declarationRange(file, document, target.name, target.line) ?: target.line?.let { AnnotationRanges.lineRange(document, it) }
            is AnnotationTarget.AtLine -> AnnotationRanges.lineRange(document, target.line)
            AnnotationTarget.FileLevel, AnnotationTarget.Skipped -> null
        }
    }

    /** Where the report says the rule was written. The definition model finds a `FileName.kt:NN`; a related location is already a path. */
    private fun ruleOf(violation: ViolationModel, definitions: DefinitionModelService?, found: MutableMap<RuleSite, RuleTarget?>): RuleTarget? {
        val site = ViolationBinding.ruleSiteOf(violation) ?: return null
        return found.getOrPut(site) {
            val label = ViolationAnnotations.ruleLabelOf(site)
            when (site) {
                is RuleSite.At -> RuleTarget.AtPath(label, site.location.path, site.location.startLine)
                is RuleSite.Declared -> (definitions?.resolve(violation.module, site.site) as? ResolvedSite.Found)?.let { RuleTarget.InFile(label, it.file, it.line) }
            }
        }
    }

    override fun apply(file: PsiFile, annotationResult: AnnotatorOutput?, holder: AnnotationHolder) {
        val output = annotationResult ?: return
        sdkCall("draw the katachi violations of ${file.name}") {
            for (planned in output.annotations) {
                // The builder is immutable: every call returns the one to go on with.
                var builder = holder.newAnnotation(severityOf(planned.severity), planned.hover).tooltip(tooltipOf(planned.hover))
                planned.rule?.let { builder = builder.withFix(GoToRuleFix(output.ports, it)) }
                builder = builder.withFix(RunCheckAgainFix(output.ports, output.file))
                builder = planned.range?.let { builder.range(it) } ?: builder.fileLevel()
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

    /** The hover is HTML: a rule's `<T>` and `&` must show, and the message's lines stay lines. */
    private fun tooltipOf(hover: String): String =
        XmlStringUtil.wrapInHtml(hover.lines().joinToString("<br>") { StringUtil.escapeXmlEntities(it) })
}
