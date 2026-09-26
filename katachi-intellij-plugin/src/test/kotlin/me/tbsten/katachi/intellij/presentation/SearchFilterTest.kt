package me.tbsten.katachi.intellij.presentation

import me.tbsten.katachi.intellij.model.ModuleTemplate
import me.tbsten.katachi.intellij.testing.module
import me.tbsten.katachi.intellij.testing.row
import me.tbsten.katachi.intellij.testing.rowsOf
import me.tbsten.katachi.intellij.testing.stringParam
import me.tbsten.katachi.intellij.testing.template
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchFilterTest {
    private val rows = rowsOf("arch-a")

    private fun names(query: String, form: FormState = FormState()) =
        searchTemplates(rows, query, form).map { it.row.template.roleName + if (it.isOutsideSearch) " (outside)" else "" }

    @Test
    fun `空の検索は全部の行を出す`() {
        assertEquals(rows.size, names("  ").size)
    }

    @Test
    fun `役割名と修飾名とtitleとsummaryとパラメータ名に大文字小文字を無視して部分一致する`() {
        assertEquals(listOf("data/Repository"), names("REPO"))
        assertEquals(listOf("domain/UseCase"), names("domain/"))
        assertEquals(listOf("domain/UseCase"), names("ユースケース"))
        assertEquals(listOf("ui/Screen"), names("画面"))
        assertEquals(listOf("data/Repository"), names("implSuffix"))
    }

    @Test
    fun `チェック中の行は一致しなくても検索外として残す`() {
        val form = FormState(selected = listOf(rows.row("ui/Screen").id))
        assertEquals(listOf("data/Repository", "ui/Screen (outside)"), names("repo", form))
    }

    @Test
    fun `一致もチェック中の行も無ければ0件`() {
        assertEquals(emptyList<String>(), names("zzz"))
    }

    @Test
    fun `200件と10モジュールの検索とフッターの導出が16ms以内に終わる`() {
        val many: List<ModuleTemplate> = (0 until 10).flatMap { m ->
            (0 until 20).map { t ->
                ModuleTemplate(
                    module(":arch$m"),
                    template("group$t/Role${m}x$t", parameters = listOf(stringParam("name"), stringParam("item", "\${name}Item")), title = "タイトル$t"),
                )
            }
        }
        val form = FormState(selected = many.take(20).map { it.id })
        fun once() {
            searchTemplates(many, "role5x1", form)
            generateBlockerOf(many, form, BusyState.Idle)
            expectedFileCountOf(many.filter { form.isSelected(it.id) }.map { fileCountSourceOf(it.template, form.inputsOf(it.id)) })
        }
        repeat(50) { once() }
        val rounds = 20
        val started = System.nanoTime()
        repeat(rounds) { once() }
        val averageMillis = (System.nanoTime() - started) / rounds / 1_000_000.0
        assertTrue("took $averageMillis ms", averageMillis < 16.0)
    }
}
