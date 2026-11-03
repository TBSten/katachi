package me.tbsten.katachi.intellij.ide

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.dsl.builder.panel
import me.tbsten.katachi.intellij.model.ConflictChoice
import me.tbsten.katachi.intellij.model.ConflictQuestion
import org.jetbrains.annotations.TestOnly
import javax.swing.Action
import javax.swing.JComponent

internal class ConflictDialog(
    private val project: Project,
    private val question: ConflictQuestion,
) : DialogWrapper(project) {
    private lateinit var overwriteButton: JBRadioButton
    private lateinit var skipButton: JBRadioButton

    init {
        title = KatachiBundle.message("conflict.title", question.index + 1, question.total, conflictNameOf(question))
        setOKButtonText(KatachiBundle.message("conflict.continue"))
        setCancelButtonText(KatachiBundle.message("conflict.stop"))
        init()
    }

    override fun createCenterPanel(): JComponent = panel {
        row { label(KatachiBundle.message("conflict.intro", question.existing.size)) }
        for (path in question.existing) {
            row { label(displayPathOf(path, question)) }
        }
        row { comment(KatachiBundle.message("conflict.note")) }
        buttonsGroup {
            row { overwriteButton = radioButton(KatachiBundle.message("conflict.overwrite")).component }
            // Not overwriting is picked first, so Enter alone never replaces a file (provisional:
            // spec 03 picks "overwrite").
            row { skipButton = radioButton(KatachiBundle.message("conflict.skip")).component.apply { isSelected = true } }
        }
    }

    // "Continue" first so Enter confirms; "Stop here" is the cancel action, so Esc and closing stop too.
    override fun createActions(): Array<Action> = arrayOf(okAction, cancelAction)

    val choice: ConflictChoice get() = conflictChoiceOf(exitCode == OK_EXIT_CODE, overwriteButton.isSelected)

    @TestOnly
    fun continueWith(overwrite: Boolean) {
        (if (overwrite) overwriteButton else skipButton).isSelected = true
        doOKAction()
    }

    @TestOnly
    fun stop() {
        doCancelAction()
    }
}

internal fun conflictChoiceOf(continued: Boolean, overwrite: Boolean): ConflictChoice = when {
    !continued -> ConflictChoice.Stop
    overwrite -> ConflictChoice.Overwrite
    else -> ConflictChoice.SkipAndContinue
}

private fun displayPathOf(path: java.nio.file.Path, question: ConflictQuestion): String {
    val root = question.templateIds.first().module.linkedRootPath
    return if (path.startsWith(root)) root.relativize(path).toString().replace('\\', '/') else path.toString()
}

private fun conflictNameOf(question: ConflictQuestion): String =
    question.templateIds.joinToString(", ") { it.template.substringAfterLast('.') }

internal object ConflictDialogs {
    @Volatile private var testAnswer: ((ConflictQuestion) -> ConflictChoice)? = null

    // Call on the EDT. A dialog that fails to show answers `ConflictChoice.Stop`, the safe answer.
    fun ask(project: Project, question: ConflictQuestion): ConflictChoice {
        testAnswer?.let { return it(question) }
        return sdkCall("ask about the existing files of ${question.templateIds.joinToString(", ") { it.template }}") {
            val dialog = ConflictDialog(project, question)
            dialog.show()
            dialog.choice
        }.getOrDefault(ConflictChoice.Stop)
    }

    @TestOnly
    fun setTestAnswer(answer: (ConflictQuestion) -> ConflictChoice, parentDisposable: Disposable) {
        testAnswer = answer
        Disposer.register(parentDisposable) { testAnswer = null }
    }
}
