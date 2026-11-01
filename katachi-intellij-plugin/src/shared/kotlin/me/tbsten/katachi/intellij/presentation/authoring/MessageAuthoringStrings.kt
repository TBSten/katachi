package me.tbsten.katachi.intellij.presentation.authoring

/**
 * [AuthoringStrings] over a message lookup: the key of each text is written here once, and an implementation only says where
 * the texts come from. The texts the source file's gutter says the same way (stale, running the check, reloading) are its keys.
 * A count is passed as text, so `1234` is not shown `1,234`.
 *
 * ```kotlin
 * val strings = object : MessageAuthoringStrings() {
 *     override fun message(key: String, vararg args: Any) = lookup(key, *args)
 * }
 * ```
 */
internal abstract class MessageAuthoringStrings : AuthoringStrings {
    /** The text of [key] with `{0}`, `{1}` ... replaced by [args]. */
    protected abstract fun message(key: String, vararg args: Any): String

    override val staleSuffix get() = message("gutter.stale.suffix")
    override val stalePrefix get() = message("gutter.stale.prefix")
    override fun files(count: Int?) = when (count) {
        null -> message("authoring.files.unknown")
        0 -> message("authoring.files.none")
        1 -> message("authoring.files.one")
        else -> message("authoring.files.many", count.toString())
    }
    override fun filesAtLeast(listed: Int) = message("authoring.files.atLeast", listed.toString())
    override val missingRequired get() = message("authoring.files.missingRequired")
    override fun modules(count: Int) = message("authoring.files.modules", count.toString())
    override fun generateFrom(roles: String) = message("authoring.gutter.generateFrom", roles)
    override val countUnknown get() = message("authoring.files.countUnknown")
    override val noFileYet get() = message("authoring.menu.noFileYet")
    override fun moreFiles(count: Int) = message("authoring.menu.moreFiles", count.toString())
    override fun runCheck(taskPath: String) = message("gutter.menu.runCheck", taskPath)
    override val reload get() = message("gutter.menu.reload")
    override val generateFromThis get() = message("authoring.menu.generate")
    override val showInToolWindow get() = message("authoring.menu.reveal")

    override fun noFileMatches(pattern: String) = message("authoring.popup.noFileMatches", pattern)
    override fun noFileIn(where: String) = message("authoring.popup.noFileIn", where)
    override fun filesIn(where: String) = message("authoring.popup.filesIn", where)
    override val projectRoot get() = message("authoring.popup.projectRoot")
    override val andMore get() = message("authoring.popup.andMore")

    override val pathCapture get() = message("authoring.completion.pathCapture")
    override val moduleCapture get() = message("authoring.completion.moduleCapture")
    override val capture get() = message("authoring.completion.capture")
    override val parameter get() = message("authoring.completion.parameter")
}
