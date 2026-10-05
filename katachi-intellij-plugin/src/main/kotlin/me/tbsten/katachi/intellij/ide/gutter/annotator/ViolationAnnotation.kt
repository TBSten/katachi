package me.tbsten.katachi.intellij.ide.gutter.annotator

import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import me.tbsten.katachi.intellij.ide.gutter.GutterPorts
import me.tbsten.katachi.intellij.model.ModuleId
import me.tbsten.katachi.intellij.presentation.gutter.AnnotationSeverity
import java.nio.file.Path

/** What `collectInformation` hands to the background: the file and where to read the answers from. */
internal class AnnotatorInput(val file: PsiFile, val virtualFile: VirtualFile, val ports: GutterPorts)

/** The definition's line "Go to the rule" opens: [label] is `ServiceRole.kt:94`. */
internal sealed interface RuleTarget {
    val label: String

    /** A file the definition model found among the test sources. */
    data class InFile(override val label: String, val file: VirtualFile, val line: Int) : RuleTarget

    /** A path the report names; the file is looked for when it is opened. */
    data class AtPath(override val label: String, val path: Path, val line: Int?) : RuleTarget
}

/** One annotation worked out in the background and drawn on the EDT; [range] is `null` for the file-level banner. */
internal data class PlannedAnnotation(
    val range: TextRange?,
    val severity: AnnotationSeverity,
    val hover: String,
    val module: ModuleId,
    val rule: RuleTarget?,
)

/** What `doAnnotate` hands to `apply`. */
internal class AnnotatorOutput(val file: VirtualFile, val ports: GutterPorts, val annotations: List<PlannedAnnotation>)
