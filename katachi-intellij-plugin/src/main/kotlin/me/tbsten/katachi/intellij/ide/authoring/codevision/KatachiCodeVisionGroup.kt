package me.tbsten.katachi.intellij.ide.authoring.codevision

import com.intellij.codeInsight.codeVision.settings.CodeVisionGroupSettingProvider

/**
 * The group the definition side's Code Vision is listed under in `Settings | Editor | Inlay Hints | Code Vision` (ide-authoring design
 * 4.3). It has no setting of its own: the platform's switch for the group is the switch.
 *
 * ```xml
 * <config.codeVisionGroupSettingProvider implementation="me.tbsten.katachi.intellij.ide.authoring.codevision.KatachiCodeVisionGroup"/>
 * ```
 */
internal class KatachiCodeVisionGroup : CodeVisionGroupSettingProvider {
    override val groupId: String get() = GROUP_ID

    override val groupName: String get() = "katachi"

    override val description: String get() = "How many files a line of a katachi definition's layout holds."

    companion object {
        /** The id the provider's `groupId` and the settings page agree on. */
        const val GROUP_ID: String = "katachi"
    }
}
