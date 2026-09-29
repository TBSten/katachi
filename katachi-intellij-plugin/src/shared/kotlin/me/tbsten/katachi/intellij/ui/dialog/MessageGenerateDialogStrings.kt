package me.tbsten.katachi.intellij.ui.dialog

/**
 * [GenerateDialogStrings] over a message lookup: the key of each text is written here once, and an
 * implementation only says where the texts come from (the IDE's bundle, or the properties files).
 *
 * ```kotlin
 * val strings = object : MessageGenerateDialogStrings() {
 *     override fun message(key: String, vararg args: Any) = lookup(key, *args)
 * }
 * ```
 */
internal abstract class MessageGenerateDialogStrings : GenerateDialogStrings {
    /** The text of [key] with `{0}`, `{1}` ... replaced by [args]. */
    protected abstract fun message(key: String, vararg args: Any): String

    override val title get() = message("dialog.title")
    override val templateLabel get() = message("dialog.template.label")
    override val definitionLabel get() = message("dialog.definition.label")
    override val targetPathLabel get() = message("dialog.targetPath.label")
    override val generate get() = message("dialog.generate")
    override val noTemplates get() = message("dialog.noTemplates")
    override fun templateRemoved(switchedTo: String) = message("dialog.templateRemoved", switchedTo)
    override val targetNew get() = message("dialog.target.new")
    override val targetEmpty get() = message("dialog.target.empty")
    override val targetHasContent get() = message("dialog.target.hasContent")
    override val chooseOne get() = message("dialog.field.chooseOne")
    override val requiredError get() = message("dialog.field.required")
    override fun notAnInt(min: Int, max: Int) = message("dialog.field.notAnInt", min, max)
    override val notAccepted get() = message("dialog.field.notAccepted")
    override val captureSeparatorError get() = message("dialog.field.captureSeparator")
    override val captureDotError get() = message("dialog.field.captureDot")
    override fun capturePathHint(name: String, markedPattern: String) = message("dialog.field.capturePathHint", name, markedPattern)
    override fun captureModuleHint(name: String, markedPattern: String) = message("dialog.field.captureModuleHint", name, markedPattern)
    override fun collapsedField(name: String, controller: String, value: String) = message("dialog.field.collapsed", name, controller, value)
    override fun generateRefused(reason: String) = message("dialog.generateRefused", reason)
}
