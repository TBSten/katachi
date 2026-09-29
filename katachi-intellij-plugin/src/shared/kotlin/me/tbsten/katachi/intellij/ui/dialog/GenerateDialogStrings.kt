package me.tbsten.katachi.intellij.ui.dialog

import me.tbsten.katachi.intellij.presentation.ValidationStrings

/**
 * The generate dialog's texts, in English by default and in Japanese for the Japanese language pack
 * (issue 8). The dialog is new, so unlike the tool window's `KatachiStrings` it is bilingual from the
 * start (decision 15). Implemented twice: over `KatachiBundle` in the IDE (`BundleGenerateDialogStrings`),
 * and over the properties files for the tests and the preview ([PropertiesGenerateDialogStrings]); both
 * only supply [MessageGenerateDialogStrings.message], so a key is written once.
 *
 * ```kotlin
 * Text(strings.templateLabel)
 * ```
 */
internal interface GenerateDialogStrings : ValidationStrings {
    val title: String

    /** The template select box's label. */
    val templateLabel: String

    /** The architecture definition select box's label (shown with two or more definitions, issue 7). */
    val definitionLabel: String

    /** The label of the path the inputs produce. */
    val targetPathLabel: String

    /** The OK button. */
    val generate: String

    /** Shown in place of the form when the definition has no template at all. */
    val noTemplates: String

    /** Said when the selected template vanished while the dialog was open; [switchedTo] is the one now selected. */
    fun templateRemoved(switchedTo: String): String

    /** The existing-file notice (issue 6): nothing there yet. */
    val targetNew: String

    /** The existing-file notice: an empty file is there, and gets the content. */
    val targetEmpty: String

    /** The existing-file notice: a file with content is there, so nothing is created. */
    val targetHasContent: String

    /** The placeholder of a select box without a value yet. */
    val chooseOne: String

    // The validation errors of a field (issue 15) are [ValidationStrings]: the same set as the tool window's.

    /** The note under a capture's field: where its value goes. [markedPattern] has the capture written `<name>`. */
    fun capturePathHint(name: String, markedPattern: String): String

    fun captureModuleHint(name: String, markedPattern: String): String

    /** The grey line of a parameter whose branch is not taken: [name] applies when [controller] is [value]. */
    fun collapsedField(name: String, controller: String, value: String): String

    /** The reason shown in the dialog when the pre-check refuses to generate. */
    fun generateRefused(reason: String): String
}
