package me.tbsten.katachi.intellij.ide.gutter.annotator

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile

/** One katachi annotation as the daemon drew it: where (a range, or the file's banner), how strong, what it says, and the fixes offered. */
internal data class DrawnAnnotation(
    val severity: HighlightSeverity,
    /** The text under the underline; `null` for the file-level banner. */
    val text: String?,
    /** The 1-based line of the underline; `null` for the banner. */
    val line: Int?,
    val description: String,
    val tooltip: String?,
    val fixes: List<String>,
)

/** What the daemon holds for [file]: the highlights of the editor, and the banners over it. */
internal fun allHighlights(project: Project, file: PsiFile, document: Document): List<HighlightInfo> {
    val daemon = DaemonCodeAnalyzer.getInstance(project) as? DaemonCodeAnalyzerImpl ?: throw AssertionError("the test daemon is not a DaemonCodeAnalyzerImpl")
    // The ranged list is the markup model's, the banners are the file-level list's alone. Nothing is de-duplicated: a mark drawn twice must show.
    return DaemonCodeAnalyzerImpl.getHighlights(document, null, project).filterNot { it.isFileLevelAnnotation } + daemon.getFileLevelHighlights(project, file)
}

/** The quick fixes registered on [info], in the order the Alt+Enter menu lists them. */
internal fun quickFixesOf(info: HighlightInfo): List<IntentionAction> {
    val found = mutableListOf<IntentionAction>()
    info.findRegisteredQuickFix<Any?> { descriptor, _ ->
        found += descriptor.action
        null
    }
    return found
}

/** The katachi annotations the daemon holds for [file] now, top to bottom (the banner first). Does not run the daemon. */
internal fun drawnAnnotations(project: Project, file: PsiFile, document: Document): List<DrawnAnnotation> =
    allHighlights(project, file, document)
        .filter { it.description?.contains("katachi: ") == true }
        .map { info ->
            val ranged = !info.isFileLevelAnnotation
            DrawnAnnotation(
                severity = info.severity,
                text = if (ranged) document.getText(TextRange(info.startOffset, info.endOffset)) else null,
                line = if (ranged) document.getLineNumber(info.startOffset) + 1 else null,
                description = info.description.orEmpty(),
                tooltip = info.toolTip,
                fixes = quickFixesOf(info).map { it.text },
            )
        }
        .sortedWith(compareBy({ it.line ?: 0 }, { it.description }))
