package me.tbsten.katachi.intellij.ide.authoring.completion

import me.tbsten.katachi.intellij.AnalysisTestBase
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider

/**
 * That the completion tests run under the Kotlin plugin mode the task asked for: K2 for `test`, K1 for `testK1`
 * (which passes `katachi.test.expectK1`). Without it, a system property that stops working would leave `testK1`
 * running in K2 and green.
 */
internal class DefinitionCompletionPluginModeTest : AnalysisTestBase() {

    fun `test 走っている Kotlin プラグインのモードは タスクが求めたものと同じ`() {
        val expectK1 = System.getProperty("katachi.test.expectK1") == "true"

        assertEquals(if (expectK1) "K1" else "K2", if (KotlinPluginModeProvider.isK2Mode()) "K2" else "K1")
    }
}
