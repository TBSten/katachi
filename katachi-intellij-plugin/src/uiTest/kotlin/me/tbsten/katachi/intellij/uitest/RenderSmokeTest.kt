package me.tbsten.katachi.intellij.uitest

import kotlinx.coroutines.runBlocking
import me.tbsten.katachi.intellij.presentation.EnglishKatachiStrings
import me.tbsten.katachi.intellij.presentation.JapaneseKatachiStrings
import me.tbsten.katachi.intellij.presentation.uiStateOf
import me.tbsten.katachi.intellij.testing.ScenarioHarness
import org.junit.Test

class RenderSmokeTest {
    @Test
    fun `シナリオの土台で読み込んだ一覧を日英それぞれ両方の幅で描ける`() = runBlocking {
        val s = ScenarioHarness(this)
        s.open()
        s.check(s.repository)
        for (strings in listOf(JapaneseKatachiStrings, EnglishKatachiStrings)) {
            val ui = uiStateOf(s.state, strings, ScenarioHarness.NOW)
            DockSize.entries.forEach { renderToolWindow(ui, it).close() }
        }
    }
}
