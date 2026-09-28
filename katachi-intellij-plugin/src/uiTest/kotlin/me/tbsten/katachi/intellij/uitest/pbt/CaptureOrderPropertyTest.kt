package me.tbsten.katachi.intellij.uitest.pbt

import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.shuffle
import io.kotest.property.checkAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import me.tbsten.katachi.intellij.model.TemplateId
import me.tbsten.katachi.intellij.presentation.FieldId
import me.tbsten.katachi.intellij.presentation.FooterUi
import me.tbsten.katachi.intellij.presentation.GenerationState
import me.tbsten.katachi.intellij.presentation.KatachiIntent
import me.tbsten.katachi.intellij.testing.ContractFixtures
import me.tbsten.katachi.intellij.testing.ManualDispatcher
import me.tbsten.katachi.intellij.testing.ScenarioHarness
import me.tbsten.katachi.intellij.testing.module
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * feature/Deep of `contract/json/capture.json` (captures `area` and `feature`, then `name` with a
 * default) filled in any order, with its form folded and opened or the row unchecked and checked
 * again in between: pressing Generate sends exactly the last value of each field, the captures
 * first, or -- while a field is empty or a capture is not one level -- is refused with that field
 * as the reason.
 */
class CaptureOrderPropertyTest {
    private val seed = System.getProperty("katachi.pbt.seed")?.toLong() ?: 20260928L
    private val scale = System.getProperty("katachi.pbt.scale")?.toDouble() ?: 1.0

    private val deep = TemplateId(module(":arch-a").id, "feature/Deep")
    private val fieldOrder = listOf("area", "feature", "name")

    /** One step: a value typed into a field, or a move that must keep every value. */
    private sealed interface Step {
        data class Type(val field: String, val value: String) : Step
        data object FoldAndOpen : Step
        data object UncheckAndCheck : Step
    }

    private val values = listOf("home", "app", "User", "", " ", "home/list", "a\\b", ".", "..", "日本語")

    /** Every field typed at least once, in a shuffled order, with retypes and moves mixed in. */
    private val stepsArb: Arb<List<Step>> = arbitrary {
        val firsts = fieldOrder.map { Step.Type(it, Arb.element(values).bind()) }
        val extras = Arb.list(
            arbitrary {
                when (Arb.int(0..3).bind()) {
                    0 -> Step.FoldAndOpen
                    1 -> Step.UncheckAndCheck
                    else -> Step.Type(Arb.element(fieldOrder).bind(), Arb.element(values).bind())
                }
            },
            0..5,
        ).bind()
        Arb.shuffle(firsts + extras).bind()
    }

    @Test
    fun `captureとパラメータをどの順に入れても生成の--argは各欄の最後の値でcaptureが先`() = runBlocking {
        val config = PropTestConfig(seed = seed, iterations = (200 * scale).toInt().coerceAtLeast(1))
        checkAll(config, stepsArb) { steps -> play(steps) }
        Unit
    }

    private fun play(steps: List<Step>) {
        val dispatcher = ManualDispatcher()
        val scope = CoroutineScope(dispatcher + SupervisorJob())
        try {
            val s = ScenarioHarness(scope, ioDispatcher = dispatcher)
            s.loadJson = ContractFixtures.json("capture")
            fun send(vararg intents: KatachiIntent) {
                s.dispatch(*intents)
                dispatcher.runAll()
            }
            send(KatachiIntent.Opened)
            send(KatachiIntent.ToggleCheck(deep))

            val last = mutableMapOf<String, String>()
            for (step in steps) {
                when (step) {
                    is Step.Type -> {
                        send(KatachiIntent.Input(FieldId(deep, step.field), step.value))
                        last[step.field] = step.value
                    }
                    Step.FoldAndOpen -> send(KatachiIntent.SetExpanded(deep, false), KatachiIntent.SetExpanded(deep, true))
                    Step.UncheckAndCheck -> send(KatachiIntent.ToggleCheck(deep), KatachiIntent.ToggleCheck(deep))
                }
            }

            val firstInvalid = fieldOrder.firstOrNull { !isValid(it, last[it]) }
            val footer = s.formFooter()
            if (firstInvalid != null) {
                assertEquals("$steps", false, footer.generateEnabled)
                assertEquals("$steps", FieldId(deep, firstInvalid), footer.reasonTarget)
                send(KatachiIntent.Generate)
                assertTrue("$steps: nothing may run", s.katachi.runs.isEmpty())
                return
            }
            assertTrue("$steps", footer is FooterUi.Form && footer.generateEnabled)
            send(KatachiIntent.Generate)
            assertTrue("$steps", s.state.generation is GenerationState.Finished)

            val expected = listOf("roleName" to "feature/Deep", "onExisting" to "fail") +
                fieldOrder.mapNotNull { name -> last.getValue(name).takeIf { it.isNotBlank() }?.let { name to it } }
            assertEquals("$steps", expected.toMap(), s.katachi.runs.single())
            assertEquals("$steps", expected.map { it.first }, s.katachi.runs.single().keys.toList())
        } finally {
            scope.cancel()
        }
    }

    /** The oracle, written apart from the plugin's validation: a capture is one level, `name` has a default. */
    private fun isValid(field: String, value: String?): Boolean {
        if (field == "name") return true
        val text = value?.trim().orEmpty()
        return text.isNotEmpty() && '/' !in value.orEmpty() && '\\' !in value.orEmpty() && text != "." && text != ".."
    }
}
