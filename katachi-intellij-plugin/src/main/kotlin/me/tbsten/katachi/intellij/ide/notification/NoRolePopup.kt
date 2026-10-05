package me.tbsten.katachi.intellij.ide.notification

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.popup.JBPopupFactory
import me.tbsten.katachi.intellij.ide.KatachiBundle
import me.tbsten.katachi.intellij.ide.definition.ResolvedSite
import me.tbsten.katachi.intellij.ide.rules.NoRoleFound
import me.tbsten.katachi.intellij.ide.rules.RuleEffects
import me.tbsten.katachi.intellij.ide.sdkCall
import me.tbsten.katachi.intellij.presentation.rules.NearbyRow
import me.tbsten.katachi.intellij.presentation.rules.ResolvedSiteView
import me.tbsten.katachi.intellij.presentation.rules.roleSuggestionDraftFor
import me.tbsten.katachi.intellij.presentation.rules.suggestedRoleNameFor
import javax.swing.JComponent

/**
 * The popup of the no-role notification (design 2.3): the nearby roles (a row jumps to the declaration; one whose
 * declaration was not found is shown but cannot be pressed), a separator, the draft of a role to copy, and the
 * definition's refresh. The sites were resolved before ([NoRoleFound]), so building the group on the EDT only
 * arranges actions.
 *
 * Row texts hold role names and paths of the user's definition: each action's text is set with
 * `mayContainMnemonic = false`, otherwise a `_` or `&` in a directory would be read as a mnemonic and vanish.
 *
 * ```kotlin
 * val group = noRolePopupGroup(found, effects)
 * showNoRolePopup(link, group)
 * ```
 */
internal fun noRolePopupGroup(found: NoRoleFound, effects: RuleEffects): DefaultActionGroup {
    val group = DefaultActionGroup()
    val decision = found.decision
    if (decision.nearby.isEmpty()) {
        group.add(RuleRow("— " + KatachiBundle.message("popup.rules.nearby.none"), enabled = false) {})
    } else {
        decision.nearby.forEachIndexed { index, row ->
            val site = found.nearbySites.getOrNull(index)
            group.add(RuleRow(nearbyRowText(row), enabled = site is ResolvedSite.Found) { (site as? ResolvedSite.Found)?.let(effects::open) })
        }
    }
    group.addSeparator()
    val draftName = suggestedRoleNameFor(decision.relativePath)
    group.add(RuleRow(KatachiBundle.message("popup.rules.copySuggestion", draftName)) { effects.copy(roleSuggestionDraftFor(decision.relativePath)) })
    group.add(RuleRow(KatachiBundle.message("popup.rules.action.refresh")) { effects.refreshDefinition() })
    return group
}

/** `domain.UseCase -> core/domain/useCase   UseCaseRole.kt:10`, or why the declaration is not known. */
internal fun nearbyRowText(row: NearbyRow): String {
    val head = KatachiBundle.message("popup.rules.nearby.row", row.roleName, row.directory)
    return when (val site = row.site) {
        is ResolvedSiteView.Found -> "$head   ${site.fileName}:${site.line}"
        is ResolvedSiteView.Unresolved -> "$head   ${KatachiBundle.message("popup.rules.unresolved", site.reason)}"
    }
}

/** Opens the popup of [group] under [component], the link that was clicked (design U-8). */
internal fun showNoRolePopup(component: JComponent, group: DefaultActionGroup) {
    sdkCall("show the nearby roles") {
        JBPopupFactory.getInstance()
            .createActionGroupPopup(
                KatachiBundle.message("popup.rules.section.nearby"),
                group,
                DataManager.getInstance().getDataContext(component),
                JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
                true,
            )
            .showUnderneathOf(component)
    }
}

/** A row of the popup: a fixed [text], which does the work of [onClick] when it is [enabled]. */
private class RuleRow(text: String, private val enabled: Boolean = true, private val onClick: () -> Unit) : DumbAwareAction() {
    init {
        templatePresentation.setText(text, false)
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = enabled
    }

    override fun actionPerformed(e: AnActionEvent) = onClick()
}
