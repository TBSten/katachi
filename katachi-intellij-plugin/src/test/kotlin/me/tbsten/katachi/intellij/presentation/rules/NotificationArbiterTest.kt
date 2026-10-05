package me.tbsten.katachi.intellij.presentation.rules

import me.tbsten.katachi.intellij.presentation.entry.NotificationDecision
import me.tbsten.katachi.intellij.testing.placementMatch
import org.junit.Assert.assertEquals
import org.junit.Test

/** One file shows at most one katachi notification, and the one it shows is the one design 2.4 ranks first. */
class NotificationArbiterTest {
    private val matches = listOf(placementMatch())
    private val noRole = NoRoleDecision.NoRole(emptyList(), untracked = false, stale = false, unexpectedUnder = null, relativePath = "a/B.kt", canOpenDefinition = false)

    private val templates = mapOf(
        "Hidden" to NotificationDecision.Hidden,
        "CreateFromTemplate" to NotificationDecision.CreateFromTemplate(matches),
        "ViewTemplate" to NotificationDecision.ViewTemplate(matches),
        "Generating" to NotificationDecision.Generating(null),
        "FailedWithCommand" to NotificationDecision.FailedWithCommand("./gradlew katachiTemplate"),
    )
    private val rules = mapOf("Hidden" to NoRoleDecision.Hidden, "NoRole" to noRole)

    @Test
    fun `テンプレートの5つと当たる Role 無しの2つのすべての組で勝者は表のとおりに1つだけ決まる`() {
        val expected = mapOf(
            ("Hidden" to "Hidden") to NotificationWinner.None,
            ("CreateFromTemplate" to "Hidden") to NotificationWinner.Template,
            ("ViewTemplate" to "Hidden") to NotificationWinner.Template,
            ("Generating" to "Hidden") to NotificationWinner.Template,
            ("FailedWithCommand" to "Hidden") to NotificationWinner.Template,
            ("Hidden" to "NoRole") to NotificationWinner.Rule,
            ("CreateFromTemplate" to "NoRole") to NotificationWinner.Rule,
            ("ViewTemplate" to "NoRole") to NotificationWinner.Rule,
            // A generation under way or failed keeps its next step on screen.
            ("Generating" to "NoRole") to NotificationWinner.Template,
            ("FailedWithCommand" to "NoRole") to NotificationWinner.Template,
        )
        for ((templateName, template) in templates) {
            for ((ruleName, rule) in rules) {
                assertEquals("$templateName x $ruleName", expected.getValue(templateName to ruleName), arbitrateNotifications(template, rule))
            }
        }
        assertEquals(templates.size * rules.size, expected.size)
    }

    @Test
    fun `どちらも出さない組のときだけ誰も勝たない`() {
        val nobody = templates.flatMap { (t, template) -> rules.map { (r, rule) -> (t to r) to arbitrateNotifications(template, rule) } }
            .filter { it.second == NotificationWinner.None }.map { it.first }

        assertEquals(listOf("Hidden" to "Hidden"), nobody)
    }
}
