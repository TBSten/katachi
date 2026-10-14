package me.tbsten.katachi.intellij.presentation.rules

import me.tbsten.katachi.intellij.data.baseline.parseBaselineText
import me.tbsten.katachi.intellij.model.MatchOutcome
import me.tbsten.katachi.intellij.testing.DefinitionFixtures
import me.tbsten.katachi.intellij.testing.definitionSnapshot
import me.tbsten.katachi.intellij.testing.noRoleInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * sample/jvm's own definition JSON and `katachi-baseline.json` (the copies in the contract goldens), put through the decision:
 * what katachi wrote is what the plugin reads. `LegacyHealthCheck.kt` is the baseline's `UnexpectedFile`.
 */
class NoRoleBaselineRealSampleTest {
    private val definition = DefinitionFixtures.definition("real-jvm-definition")
    private val snapshot = definitionSnapshot(definition)
    private val baseline = parseBaselineText(
        NoRoleBaselineRealSampleTest::class.java.getResourceAsStream("/contract/gutter/sources/jvm/katachi-baseline.json")!!.use { String(it.readBytes()) },
    )

    private fun decide(relative: String): NoRoleDecision.NoRole {
        val input = noRoleInput(relative, snapshot, baseline = baseline)
        return decideNoRoleNotification(input) as? NoRoleDecision.NoRole ?: throw AssertionError("not a NoRole: $relative ${input.roles?.outcome}")
    }

    @Test
    fun `definition json の architecture に baseline のパスが入っている`() {
        assertEquals("katachi-baseline.json", definition.architecture.baseline)
    }

    @Test
    fun `LegacyHealthCheck は baseline で許容中 baseline に無い同じ形のファイルは許容されない`() {
        val legacy = "src/main/kotlin/com/example/service/LegacyHealthCheck.kt"
        assertEquals(MatchOutcome.Unexpected, snapshot.definition?.let { noRoleInput(legacy, snapshot).roles?.outcome })
        assertEquals(BaselineVerdict.Held("UnexpectedFile"), decide(legacy).baseline)
        assertEquals(BaselineVerdict.NotHeld, decide("src/main/kotlin/com/example/service/NewHealthCheck.kt").baseline)
    }

    @Test
    fun `LegacyStatusController は CaptureMismatch の項目で許容中`() {
        val input = noRoleInput("src/main/kotlin/com/example/controller/health/LegacyStatusController.kt", snapshot, baseline = baseline)
        val decision = decideNoRoleNotification(input)
        assertTrue("$decision", decision is NoRoleDecision.NoRole)
        assertEquals(BaselineVerdict.Held("CaptureMismatch"), (decision as NoRoleDecision.NoRole).baseline)
    }
}
