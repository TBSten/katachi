package me.tbsten.katachi.intellij.ide.rules

import com.intellij.notification.Notification
import com.intellij.notification.Notifications
import com.intellij.testFramework.PlatformTestUtil
import me.tbsten.katachi.intellij.AnalysisTestBase
import me.tbsten.katachi.intellij.ide.KatachiBundle
import me.tbsten.katachi.intellij.ide.KatachiNotifications
import me.tbsten.katachi.intellij.ide.definition.ResolvedSite
import java.util.Collections

/** A rule operation that fails is told in a balloon naming the operation (design 2.3): open, copy and settings each have their own. */
internal class RuleEffectsFailureTest : AnalysisTestBase() {
    private val balloons: MutableList<Notification> = Collections.synchronizedList(mutableListOf())

    override fun setUp() {
        super.setUp()
        project.messageBus.connect(testRootDisposable).subscribe(
            Notifications.TOPIC,
            object : Notifications {
                override fun notify(notification: Notification) {
                    if (notification.groupId == KatachiNotifications.GROUP_ID) balloons += notification
                }
            },
        )
    }

    private fun failing(): Nothing = throw IllegalStateException("boom")

    private fun balloonContents(): List<String> {
        PlatformTestUtil.waitWithEventsDispatching({ "balloons: ${balloons.map { it.content }}" }, { balloons.isNotEmpty() }, 10)
        return balloons.map { it.content }
    }

    private fun expected(operationKey: String) = KatachiBundle.message("rules.failed", KatachiBundle.message(operationKey), "boom")

    fun `test 定義を開くのに失敗したら 開くの操作名で balloon を出す`() {
        val file = myFixture.configureByText("Defs.kt", "")
        RuleEffectsImpl(project, navigate = { _, _ -> failing() }).open(ResolvedSite.Found(file.virtualFile, 3))

        assertEquals(listOf(expected("rules.failed.open")), balloonContents())
    }

    fun `test 下書きのコピーに失敗したら コピーの操作名で balloon を出す`() {
        RuleEffectsImpl(project, setClipboard = { failing() }).copy("role draft")

        assertEquals(listOf(expected("rules.failed.copy")), balloonContents())
    }

    fun `test 設定を開くのに失敗したら 設定の操作名で balloon を出す`() {
        RuleEffectsImpl(project, showSettings = { failing() }).openSettings()

        assertEquals(listOf(expected("rules.failed.settings")), balloonContents())
    }

    fun `test 失敗しなければ balloon を出さず 渡した行は0始まりで開く`() {
        val file = myFixture.configureByText("Defs.kt", "")
        var opened: Pair<String, Int>? = null
        RuleEffectsImpl(project, navigate = { f, line -> opened = f.name to line }).open(ResolvedSite.Found(file.virtualFile, 3))

        assertEquals("Defs.kt" to 2, opened)
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        assertTrue(balloons.toString(), balloons.isEmpty())
    }
}
