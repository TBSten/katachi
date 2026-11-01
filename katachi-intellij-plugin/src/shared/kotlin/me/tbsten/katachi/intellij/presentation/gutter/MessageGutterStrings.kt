package me.tbsten.katachi.intellij.presentation.gutter

/**
 * [GutterStrings] over a message lookup: the key of each text is written here once, and an implementation only says where
 * the texts come from (the IDE's bundle, or the properties files). A count is passed as text, so `1234` is not shown `1,234`.
 *
 * ```kotlin
 * val strings = object : MessageGutterStrings() {
 *     override fun message(key: String, vararg args: Any) = lookup(key, *args)
 * }
 * ```
 */
internal abstract class MessageGutterStrings : GutterStrings {
    /** The text of [key] with `{0}`, `{1}` ... replaced by [args]. */
    protected abstract fun message(key: String, vararg args: Any): String

    override val staleSuffix get() = message("gutter.stale.suffix")
    override fun reloadFailed(summary: String) = message("gutter.tooltip.reloadFailed", summary)
    override val failureCompilation get() = message("gutter.tooltip.failure.compilation")
    override val failureTaskNotFound get() = message("gutter.tooltip.failure.taskNotFound")
    override val failureArchitectureNotSet get() = message("gutter.tooltip.failure.architectureNotSet")
    override val failureProcessorRejected get() = message("gutter.tooltip.failure.processorRejected")
    override val failureDefinitionRejected get() = message("gutter.tooltip.failure.definitionRejected")
    override val failureGradle get() = message("gutter.tooltip.failure.gradle")
    override val failureJsonMissing get() = message("gutter.tooltip.failure.jsonMissing")
    override val failureJsonMalformed get() = message("gutter.tooltip.failure.jsonMalformed")
    override val failureJsonIncompatible get() = message("gutter.tooltip.failure.jsonIncompatible")
    override val failureCancelled get() = message("gutter.tooltip.failure.cancelled")
    override val notComputed get() = message("gutter.tooltip.notComputed")
    override fun allowedBy(rule: String) = message("gutter.tooltip.allowedBy", rule)
    override fun prohibitedBy(rule: String) = message("gutter.tooltip.prohibitedBy", rule)
    override fun mismatched(rule: String) = message("gutter.tooltip.mismatched", rule)
    override val unexpected get() = message("gutter.tooltip.unexpected")
    override fun judgedByNewer(verdict: String) = message("gutter.tooltip.judgedByNewer", verdict)
    override val unrecordedRule get() = message("gutter.rule.unrecorded")

    override val menuReloadToSee get() = message("gutter.menu.reloadToSee")
    override fun menuAllowedBy(rule: String) = message("gutter.menu.allowedBy", rule)
    override fun menuProhibitedBy(rule: String) = message("gutter.menu.prohibitedBy", rule)
    override fun menuMismatched(rule: String) = message("gutter.menu.mismatched", rule)
    override val menuUnexpected get() = message("gutter.menu.unexpected")
    override fun menuJudgedByNewer(verdict: String) = message("gutter.menu.judgedByNewer", verdict)
    override fun missingSection(count: Int) = message("gutter.menu.missing", count.toString())
    override fun declarationNotFound(reason: String) = message("gutter.menu.declarationNotFound", reason)
    override fun roleRow(name: String) = message("gutter.menu.role", name)
    override fun siblingsSection(role: String, count: Int?, stale: Boolean) = when {
        count == null && stale -> message("gutter.menu.siblings.stale", role)
        count == null -> message("gutter.menu.siblings", role)
        stale -> message("gutter.menu.siblings.countStale", role, count.toString())
        else -> message("gutter.menu.siblings.count", role, count.toString())
    }
    override val noSibling get() = message("gutter.menu.siblings.none")
    override fun moreSiblings(count: Int) = message("gutter.menu.siblings.more", count.toString())
    override fun newFromTemplate(role: String) = message("gutter.menu.newFromTemplate", role)
    override val checking get() = message("gutter.menu.checking")
    override fun runCheck(taskPath: String) = message("gutter.menu.runCheck", taskPath)
    override val reload get() = message("gutter.menu.reload")

    override val stalePrefix get() = message("gutter.stale.prefix")
    override fun rejected(by: String, declaration: String) =
        if (by.isEmpty()) message("gutter.hover.rejected", declaration) else message("gutter.hover.rejectedBy", by, declaration)
    override fun declaredAt(site: String) = message("gutter.hover.declaredAt", site)
}
