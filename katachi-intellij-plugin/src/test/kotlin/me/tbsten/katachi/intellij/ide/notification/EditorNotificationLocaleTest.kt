package me.tbsten.katachi.intellij.ide.notification

import com.intellij.DynamicBundle
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.l10n.LocalizationStateService
import com.intellij.openapi.extensions.PluginId
import me.tbsten.katachi.intellij.ide.KatachiBundle

/** The notification's text follows the IDE's language: English by default, Japanese when selected (issue 8, research-i18n.md section 5). */
internal class EditorNotificationLocaleTest : EditorNotificationTestBase() {

    // covers: 論点8
    fun `test 既定のロケールでは英語の文言が出る`() {
        loadIndex()

        val panel = panelOf(open("feature/a/ui/Home.kt", ""))!!

        assertEquals("You can create this file from a template", panel.text)
        assertEquals(listOf("Create"), panel.links.map { it.text })
    }

    // covers: 論点8
    fun `test 日本語を選ぶと日本語の文言が出る`() {
        loadIndex()
        val localization = LocalizationStateService.getInstance() ?: throw AssertionError("No LocalizationStateService")
        val before = localization.selectedLocale
        installJapaneseLanguagePack()
        try {
            // true: applied now; false leaves a restart pending, and until it the IDE keeps its old language.
            localization.setSelectedLocale("ja", true)
            clearBundleCaches()

            val panel = panelOf(open("feature/a/ui/Home.kt", ""))!!

            assertEquals("テンプレートから作れます", panel.text)
            assertEquals(listOf("作成する"), panel.links.map { it.text })
        } finally {
            localization.setSelectedLocale(before, true)
            clearBundleCaches()
        }
    }

    /**
     * A language pack for Japanese, as the Japanese Language Pack plugin declares one: without it
     * the IDE falls back to English whatever is selected. It points at this plugin, whose
     * `KatachiBundle_ja` is then found.
     */
    private fun installJapaneseLanguagePack() {
        val pack = DynamicBundle.LanguageBundleEP().apply {
            locale = "ja"
            displayName = "Japanese"
            pluginDescriptor = PluginManagerCore.getPlugin(PluginId.getId("me.tbsten.katachi.intellij"))
        }
        DynamicBundle.LanguageBundleEP.EP_NAME.point.registerExtension(pack, testRootDisposable)
    }

    /** The platform's bundle cache, and the bundle [KatachiBundle] holds on to itself. */
    private fun clearBundleCaches() {
        DynamicBundle.clearCache()
        KatachiBundle.clearLocaleCache()
    }
}
