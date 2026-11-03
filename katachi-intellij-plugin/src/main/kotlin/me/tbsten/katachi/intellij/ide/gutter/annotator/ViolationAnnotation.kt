package me.tbsten.katachi.intellij.ide.gutter.annotator

import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import me.tbsten.katachi.intellij.ide.gutter.GutterPorts
import me.tbsten.katachi.intellij.model.ModuleId
import me.tbsten.katachi.intellij.presentation.gutter.AnnotationSeverity
import java.nio.file.Path

internal class AnnotatorInput(val file: PsiFile, val virtualFile: VirtualFile, val ports: GutterPorts)

internal sealed interface RuleTarget {
    val label: String

    data class InFile(override val label: String, val file: VirtualFile, val line: Int) : RuleTarget

    data class AtPath(override val label: String, val path: Path, val line: Int?) : RuleTarget
}

internal data class PlannedAnnotation(
    val range: TextRange?,
    val severity: AnnotationSeverity,
    val hover: String,
    val module: ModuleId,
    val rule: RuleTarget?,
)

internal class AnnotatorOutput(val file: VirtualFile, val ports: GutterPorts, val annotations: List<PlannedAnnotation>)
