package me.tbsten.katachi.intellij.presentation

import me.tbsten.katachi.intellij.model.GenerationFailure
import me.tbsten.katachi.intellij.model.GenerationItemReport
import me.tbsten.katachi.intellij.model.GenerationItemResult
import me.tbsten.katachi.intellij.model.GenerationReport
import me.tbsten.katachi.intellij.testing.booleanParam
import me.tbsten.katachi.intellij.testing.module
import me.tbsten.katachi.intellij.testing.previewFailed
import me.tbsten.katachi.intellij.testing.rows
import me.tbsten.katachi.intellij.testing.rowsOf
import me.tbsten.katachi.intellij.testing.stringParam
import me.tbsten.katachi.intellij.testing.template
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FormOperationsTest {
    private val list = rows(
        template("a/One", parameters = listOf(stringParam("name"), booleanParam("flag"))),
        template("a/Two"),
        template("a/Three"),
        previewFailed("a/Broken"),
    )
    private val one = list[0].id
    private val two = list[1].id
    private val three = list[2].id

    @Test
    fun `チェックするとフォームが開き外すと畳んで入力は残る`() {
        var form = toggleCheck(FormState(), list, one)
        assertEquals(setOf(one), form.expanded)
        form = inputField(form, list, FieldId(one, "name"), "User")
        form = toggleCheck(form, list, one)
        assertEquals(emptyList<Any>(), form.selected)
        assertEquals(emptySet<Any>(), form.expanded)
        form = toggleCheck(form, list, one)
        assertEquals("User", form.inputOf(FieldId(one, "name")))
    }

    @Test
    fun `プレビュー失敗の行はチェックできない`() {
        assertEquals(FormState(), toggleCheck(FormState(), list, list[3].id))
    }

    @Test
    fun `チェックしたままフォームだけ畳める`() {
        val form = setExpanded(toggleCheck(FormState(), list, one), one, expanded = false)
        assertEquals(listOf(one), form.selected)
        assertEquals(emptySet<Any>(), form.expanded)
    }

    private val report = GenerationReport(
        listOf(
            GenerationItemReport(one, GenerationItemResult.Generated(emptyList())),
            GenerationItemReport(two, GenerationItemResult.Failed(GenerationFailure.Katachi(listOf("boom")), emptyList())),
            GenerationItemReport(three, GenerationItemResult.NotRun),
        ),
    )
    private val afterGeneration = FormState(
        selected = listOf(one, two, three),
        expanded = emptySet(),
        inputs = mapOf(one to mapOf("name" to "User", "flag" to "false"), two to mapOf("name" to "User")),
        onExisting = OnExistingChoice.Skip,
    )

    @Test
    fun `残りをやり直すと成功した行を外し失敗と未実行の行はチェックと値を残して開く`() {
        val form = retryRemaining(afterGeneration, report)
        assertEquals(listOf(two, three), form.selected)
        assertEquals(setOf(two, three), form.expanded)
        assertEquals(mapOf("name" to "User"), form.inputsOf(two))
    }

    @Test
    fun `スキップした行もやり直しの対象から外す`() {
        val skipped = GenerationReport(listOf(GenerationItemReport(one, GenerationItemResult.Skipped(emptyList()))))
        assertEquals(emptyList<Any>(), retryRemaining(FormState(selected = listOf(one)), skipped).selected)
    }

    @Test
    fun `続けて生成はStringの欄だけ空にしてほかの値とチェックを残す`() {
        val form = continueGenerating(afterGeneration, list)
        assertEquals(mapOf("flag" to "false"), form.inputsOf(one))
        assertEquals(emptyMap<String, String>(), form.inputsOf(two))
        assertEquals(listOf(one, two, three), form.selected)
        assertEquals(setOf(one, two, three), form.expanded)
    }

    @Test
    fun `チェックを外すは入力も捨てて最初の状態に戻し既存ファイルの扱いは残す`() {
        assertEquals(FormState(onExisting = OnExistingChoice.Skip), uncheckAll(afterGeneration))
    }

    @Test
    fun `同じroleNameでもモジュールが違えば別の行としてチェックする`() {
        val a = rowsOf("arch-a", module(":arch-a"))
        val b = rowsOf("arch-b", module(":arch-b"))
        val all = a + b
        val inA = all.first { it.template.roleName == "data/Repository" }.id
        val inB = all.last { it.template.roleName == "data/Repository" }.id
        assertTrue(inA != inB)
        assertEquals(listOf(inB), toggleCheck(FormState(), all, inB).selected)
    }
}
