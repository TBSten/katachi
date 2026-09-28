package me.tbsten.katachi.intellij.ui.dialog

/**
 * The generate dialog's texts, in English by default and in Japanese for the Japanese language pack
 * (issue 8). The dialog is new, so unlike the tool window's `KatachiStrings` it is bilingual from the
 * start (decision 15). Implemented by B1 twice: over `KatachiBundle` in the IDE, and over the
 * properties files for the tests and the preview.
 *
 * B1 and D2 add the members the dialog needs (validation errors, the existing-file notices); the
 * ones here are the frame every version has.
 *
 * ```kotlin
 * Text(strings.templateLabel)
 * ```
 */
internal interface GenerateDialogStrings {
    val title: String

    /** The template select box's label. */
    val templateLabel: String

    /** The architecture definition select box's label (shown with two or more definitions, issue 7). */
    val definitionLabel: String

    /** The label of the path the inputs produce. */
    val targetPathLabel: String

    /** The OK button. */
    val generate: String
}
