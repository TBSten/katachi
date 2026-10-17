package me.tbsten.katachi.intellij.ide.authoring.errors

import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import me.tbsten.katachi.intellij.ide.definition.DefinitionModelService
import me.tbsten.katachi.intellij.ide.gutter.GutterPorts
import me.tbsten.katachi.intellij.presentation.authoring.DefinitionMarkSource
import me.tbsten.katachi.intellij.presentation.gutter.AnnotationSeverity

/** What `collectInformation` hands to the background: the file and where to read the definition from. */
internal class DefinitionErrorInput(val file: PsiFile, val virtualFile: VirtualFile, val ports: GutterPorts)

/** One underline worked out in the background and drawn on the EDT. [fullMessage] is the whole message of a mistake, for "Show the whole message"; `null` for a problem. */
internal data class PlannedDefinitionMark(
    val range: TextRange,
    val severity: AnnotationSeverity,
    val hover: String,
    val source: DefinitionMarkSource,
    val fullMessage: String?,
)

/** What `doAnnotate` hands to `apply`. [definitions] is the service "Reload Definition" asks to read again. */
internal class DefinitionErrorOutput(val definitions: DefinitionModelService, val marks: List<PlannedDefinitionMark>)
