package me.tbsten.katachi.intellij.ide

import com.intellij.DynamicBundle
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.l10n.LocalizationStateService
import com.intellij.openapi.extensions.PluginId
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import me.tbsten.katachi.intellij.presentation.EnglishKatachiStrings
import me.tbsten.katachi.intellij.presentation.JapaneseKatachiStrings

/** The tool window speaks the language `KatachiBundle` resolves to, as the menus and notifications do. */
internal class ToolWindowStringsLocaleTest : BasePlatformTestCase() {

    fun `test 既定のロケールでは Tool Window の文言が英語になる`() {
        assertSame(EnglishKatachiStrings, toolWindowStrings())
    }

    fun `test 日本語を選ぶと Tool Window の文言が日本語になる`() {
        val localization = LocalizationStateService.getInstance() ?: throw AssertionError("No LocalizationStateService")
        val before = localization.selectedLocale
        // A language pack for Japanese, pointing at this plugin; without one the IDE stays in English.
        val pack = DynamicBundle.LanguageBundleEP().apply {
            locale = "ja"
            displayName = "Japanese"
            pluginDescriptor = PluginManagerCore.getPlugin(PluginId.getId("me.tbsten.katachi.intellij"))
        }
        DynamicBundle.LanguageBundleEP.EP_NAME.point.registerExtension(pack, testRootDisposable)
        try {
            localization.setSelectedLocale("ja", true)
            DynamicBundle.clearCache()

            assertSame(JapaneseKatachiStrings, toolWindowStrings())
        } finally {
            localization.setSelectedLocale(before, true)
            DynamicBundle.clearCache()
        }
    }
}
