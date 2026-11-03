package me.tbsten.katachi.intellij.ide.authoring.errors

import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import me.tbsten.katachi.intellij.ide.definition.DefinitionModelService
import me.tbsten.katachi.intellij.ide.gutter.GutterPorts
import me.tbsten.katachi.intellij.presentation.authoring.DefinitionMarkSource
import me.tbsten.katachi.intellij.presentation.gutter.AnnotationSeverity

internal class DefinitionErrorInput(val file: PsiFile, val virtualFile: VirtualFile, val ports: GutterPorts)

internal data class PlannedDefinitionMark(
    val range: TextRange,
    val severity: AnnotationSeverity,
    val hover: String,
    val source: DefinitionMarkSource,
    val fullMessage: String?,
)

internal class DefinitionErrorOutput(val definitions: DefinitionModelService, val marks: List<PlannedDefinitionMark>)
