package me.tbsten.katachi.intellij.ide.authoring.codevision

import com.intellij.codeInsight.codeVision.settings.CodeVisionGroupSettingProvider
import me.tbsten.katachi.intellij.ide.KatachiBundle

internal class KatachiCodeVisionGroup : CodeVisionGroupSettingProvider {
    override val groupId: String get() = GROUP_ID

    override val groupName: String get() = "katachi"

    override val description: String get() = KatachiBundle.message("authoring.codevision.description")

    companion object {
        const val GROUP_ID: String = "katachi"
    }
}
