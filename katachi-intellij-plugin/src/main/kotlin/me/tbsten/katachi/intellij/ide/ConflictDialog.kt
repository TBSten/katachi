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

/**
 * The only dialog of the plugin (spec 03 "衝突ダイアログ"): the files that already exist, as
 * katachiTemplate reported them, and what to do about the whole template.
 *
 * ```
 * ┌ Files already exist (2 / 3: Repository) ─────────────┐
 * │ 📄 data/…/UserRepositoryImpl.kt                      │
 * │ ( ) Overwrite  (•) Do not write this template and go on │
 * │                          [Stop Here]  [Continue]     │
 * └──────────────────────────────────────────────────────┘
 * ```
 */
internal class ConflictDialog(
    private val project: Project,
    private val question: ConflictQuestion,
) : DialogWrapper(project) {
    private lateinit var overwriteButton: JBRadioButton
    private lateinit var skipButton: JBRadioButton

    init {
        title = KatachiBundle.message("conflict.title", question.index + 1, question.total, question.templateId.roleName.substringAfterLast('/'))
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

    /** What the user picked; closing the dialog any other way stops (the safe answer). */
    val choice: ConflictChoice get() = conflictChoiceOf(exitCode == OK_EXIT_CODE, overwriteButton.isSelected)

    /** Picks the radio button and presses "Continue", for tests that cannot click a modal dialog. */
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

/** "Continue" with the radio button, or "stop here". */
internal fun conflictChoiceOf(continued: Boolean, overwrite: Boolean): ConflictChoice = when {
    !continued -> ConflictChoice.Stop
    overwrite -> ConflictChoice.Overwrite
    else -> ConflictChoice.SkipAndContinue
}

/** The path shown in the dialog: relative to the template's linked root when inside it. */
private fun displayPathOf(path: java.nio.file.Path, question: ConflictQuestion): String {
    val root = question.templateId.module.linkedRootPath
    return if (path.startsWith(root)) root.relativize(path).toString().replace('\\', '/') else path.toString()
}

/**
 * Shows [ConflictDialog], or answers with the test hook when one is set, as `TestDialogManager` does
 * for `Messages`: a modal dialog cannot be clicked in a headless test.
 */
internal object ConflictDialogs {
    @Volatile private var testAnswer: ((ConflictQuestion) -> ConflictChoice)? = null

    /**
     * Call on the EDT. A dialog the IDE fails to show answers [ConflictChoice.Stop], the safe answer,
     * which the result shows at that template.
     */
    fun ask(project: Project, question: ConflictQuestion): ConflictChoice {
        testAnswer?.let { return it(question) }
        return sdkCall("ask about the existing files of ${question.templateId.roleName}") {
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
