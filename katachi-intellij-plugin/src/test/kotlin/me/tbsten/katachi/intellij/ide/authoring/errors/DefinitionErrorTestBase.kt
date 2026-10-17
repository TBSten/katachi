package me.tbsten.katachi.intellij.ide.authoring.errors

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.lang.ExternalLanguageAnnotators
import com.intellij.psi.PsiFile
import me.tbsten.katachi.intellij.ide.definition.ResolvedSite
import me.tbsten.katachi.intellij.ide.gutter.GutterTestBase
import me.tbsten.katachi.intellij.ide.gutter.LIGHT_ROOT
import me.tbsten.katachi.intellij.ide.gutter.annotator.DrawnAnnotation
import me.tbsten.katachi.intellij.ide.gutter.annotator.allHighlights
import me.tbsten.katachi.intellij.ide.gutter.annotator.drawnAnnotations
import me.tbsten.katachi.intellij.ide.gutter.annotator.quickFixesOf
import me.tbsten.katachi.intellij.model.DeclarationSiteRef
import me.tbsten.katachi.intellij.model.DefinitionErrorModel
import me.tbsten.katachi.intellij.model.DefinitionErrorPhase
import me.tbsten.katachi.intellij.model.DefinitionErrors
import me.tbsten.katachi.intellij.model.DefinitionErrorsEvent
import me.tbsten.katachi.intellij.model.DefinitionProblem
import me.tbsten.katachi.intellij.model.DefinitionState
import me.tbsten.katachi.intellij.model.Freshness
import me.tbsten.katachi.intellij.testing.AnnotationFixtures
import me.tbsten.katachi.intellij.testing.definitionModel
import me.tbsten.katachi.intellij.testing.definitionSnapshot
import me.tbsten.katachi.intellij.testing.layoutEntry
import me.tbsten.katachi.intellij.testing.module
import me.tbsten.katachi.intellij.testing.roleModel
import org.jetbrains.kotlin.idea.KotlinLanguage
import java.time.Instant

/**
 * The world of the platform tests of [DefinitionErrorAnnotator]: a definition file `ScreenRole.kt` of [LINES] lines
 * (`// line n`), the real PSI and daemon, and the foundation's fake service whose mistakes a test scripts. [open] puts the
 * file in the editor and tells the fake that `ScreenRole.kt:n` is a line of it.
 */
internal abstract class DefinitionErrorTestBase : GutterTestBase() {
    lateinit var annotator: DefinitionErrorAnnotator

    /** The texts "Show the whole message" put in a dialog. */
    val shown: MutableList<String> = java.util.Collections.synchronizedList(mutableListOf())

    override fun setUp() {
        super.setUp()
        annotator = DefinitionErrorAnnotator({ ports() }, { _, text -> shown += text })
        ExternalLanguageAnnotators.INSTANCE.addExplicitExtension(KotlinLanguage.INSTANCE, annotator, testRootDisposable)
    }

    /** The source of a definition file: the line n is `// line n`. */
    fun sourceOf(lines: Int = LINES): String = (1..lines).joinToString("\n", postfix = "\n") { "// line $it" }

    /** Opens `ScreenRole.kt`; every `ScreenRole.kt:n` the fake is asked about is a line of it. */
    fun openDefinition(text: String = sourceOf()): PsiFile {
        val file = open(SCREEN_ROLE, text)
        val virtualFile = file.virtualFile
        for (line in 1..40) definitions.resolveWith(DeclarationSiteRef(SCREEN_ROLE, line), ResolvedSite.Found(virtualFile, line))
        return file
    }

    fun mistake(line: Int?, message: String = "`.template { }` at ScreenRole.kt:$line is attached to a wildcard.\nUse a name.") = DefinitionErrorModel(
        AnnotationFixtures.MODULE, DefinitionErrorPhase.Processor, "internalDefinitionJson", "me.tbsten.katachi.dsl.KatachiTemplateOnWildcardException",
        message, line?.let { DeclarationSiteRef(SCREEN_ROLE, it) },
    )

    /** The definition is read and [mistakes] were found in it. */
    fun rejected(vararg mistakes: DefinitionErrorModel) {
        definitions.emit(DefinitionState.Ready(listOf(snapshot), Freshness.Fresh))
        definitions.emitErrors(DefinitionErrorsEvent.LoadRejected(mistakes.toList(), tick()))
    }

    /** The definition is read with [problems] in it and no mistake. */
    fun readWith(vararg problems: DefinitionProblem, templateAt: Pair<String, Int>? = null) {
        val role = roleModel(
            "ui.Screen",
            layoutEntry("src/*Screen.kt").let { entry ->
                if (templateAt == null) entry else entry.copy(template = me.tbsten.katachi.intellij.model.TemplateRef(templateAt.first, null, null, DeclarationSiteRef(SCREEN_ROLE, templateAt.second)))
            },
        )
        snapshot = definitionSnapshot(definitionModel(role, projectRoot = snapshot.definition!!.projectRoot ?: LIGHT_ROOT).copy(problems = problems.toList()), module(":arch"))
        definitions.emit(DefinitionState.Ready(listOf(snapshot), Freshness.Fresh))
        definitions.emitErrors(DefinitionErrors.None)
    }

    /** The katachi marks of the open file after the test restarted the daemon. */
    fun annotated(): List<DrawnAnnotation> {
        refresherWatch.reset()
        DaemonCodeAnalyzer.getInstance(project).restart(myFixture.file, "katachi test: the definition errors changed")
        myFixture.doHighlighting()
        return drawnAnnotations(project, myFixture.file, myFixture.editor.document)
    }

    /** The marks after the real refresher drew for what changed since the last call; the test never restarts the daemon. */
    fun annotatedByRefresher(): List<DrawnAnnotation> {
        refresherWatch.settle()
        myFixture.doHighlighting()
        return drawnAnnotations(project, myFixture.file, myFixture.editor.document)
    }

    /** The quick fixes registered on the mark that says [description], in the order Alt+Enter lists them. */
    fun fixesOn(description: String) = quickFixesOf(
        allHighlights(project, myFixture.file, myFixture.editor.document).firstOrNull { it.description?.contains(description) == true }
            ?: throw AssertionError("No mark says \"$description\""),
    )

    companion object {
        const val SCREEN_ROLE = "ScreenRole.kt"
        const val LINES = 12
        val EPOCH: Instant = Instant.parse("2026-10-18T00:00:00Z")
    }
}
