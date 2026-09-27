package me.tbsten.katachi.intellij.presentation

import me.tbsten.katachi.intellij.testing.previewFailed
import me.tbsten.katachi.intellij.testing.rows
import me.tbsten.katachi.intellij.testing.stringParam
import me.tbsten.katachi.intellij.testing.template
import org.junit.Assert.assertEquals
import org.junit.Test

class ReloadMergeTest {
    private val before = rows(
        template("data/Repository", parameters = listOf(stringParam("name"), stringParam("item", "String"))),
        template("domain/Service"),
    )
    private val repository = before[0].id
    private val service = before[1].id

    private val form = FormState(
        selected = listOf(repository, service),
        expanded = setOf(repository, service),
        inputs = mapOf(repository to mapOf("name" to "User", "item" to "Long"), service to mapOf("name" to "User")),
        unlinked = setOf(FieldId(repository, "item")),
    )

    @Test
    fun `roleNameとパラメータ名が合う限りチェックと開閉と入力を残す`() {
        val result = mergeAfterReload(form, before)
        assertEquals(form, result.form)
        assertEquals(emptyList<Any>(), result.removedTemplates)
    }

    @Test
    fun `消えたパラメータの入力と連動の切断だけを捨てる`() {
        val after = rows(template("data/Repository", parameters = listOf(stringParam("name"))), template("domain/Service"))
        val result = mergeAfterReload(form, after)
        assertEquals(mapOf("name" to "User"), result.form.inputsOf(repository))
        assertEquals(emptySet<FieldId>(), result.form.unlinked)
        assertEquals(listOf(repository, service), result.form.selected)
    }

    @Test
    fun `消えたテンプレートはチェックを外して名前を返す`() {
        val result = mergeAfterReload(form, rows(before[0].template))
        assertEquals(listOf(repository), result.form.selected)
        assertEquals(listOf(service), result.removedTemplates)
        assertEquals(setOf(repository), result.form.expanded)
    }

    @Test
    fun `プレビューに失敗するようになったテンプレートもチェックを外す`() {
        val result = mergeAfterReload(form, rows(before[0].template, previewFailed("domain/Service")))
        assertEquals(listOf(service), result.removedTemplates)
    }

    @Test
    fun `既存ファイルの扱いは再読み込みで変えない`() {
        val result = mergeAfterReload(form.copy(onExisting = OnExistingChoice.Overwrite), before)
        assertEquals(OnExistingChoice.Overwrite, result.form.onExisting)
    }
}
