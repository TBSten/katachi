package me.tbsten.katachi.intellij.presentation

import me.tbsten.katachi.intellij.model.ModuleTemplate
import me.tbsten.katachi.intellij.testing.module
import me.tbsten.katachi.intellij.testing.row
import me.tbsten.katachi.intellij.testing.rows
import me.tbsten.katachi.intellij.testing.rowsOf
import me.tbsten.katachi.intellij.testing.stringParam
import me.tbsten.katachi.intellij.testing.template
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkedParametersTest {
    private val list: List<ModuleTemplate> = rows(
        template("data.Repository", parameters = listOf(stringParam("name"), stringParam("item", "String"))),
        template("domain.Service", parameters = listOf(stringParam("name"))),
        template("domain.UseCase", parameters = listOf(stringParam("name"))),
    )
    private val repository = list[0].id
    private val service = list[1].id
    private val useCase = list[2].id

    private fun field(row: Int, name: String = "name") = FieldId(list[row].id, name)

    private fun checked(vararg rows: Int) = rows.fold(FormState()) { form, row -> toggleCheck(form, list, list[row].id) }

    @Test
    fun `入力するとチェック中の同名同型の欄に伝わり連動の印が付く`() {
        val form = inputField(checked(0, 1), list, field(0), "User")
        assertEquals("User", form.inputOf(field(1)))
        assertTrue(isLinked(field(0), list, form))
        assertTrue(isLinked(field(1), list, form))
    }

    @Test
    fun `同名の欄が1つだけなら連動の印を出さない`() {
        val form = inputField(checked(0), list, field(0), "User")
        assertFalse(isLinked(field(0), list, form))
    }

    @Test
    fun `連動中の欄を別の値に書き換えるとその欄だけ連動が切れる`() {
        var form = inputField(checked(0, 1, 2), list, field(0), "User")
        form = inputField(form, list, field(1), "Admin")
        assertTrue(isUnlinked(field(1), form))
        assertEquals("User", form.inputOf(field(2)))
        form = inputField(form, list, field(0), "Member")
        assertEquals("Admin", form.inputOf(field(1)))
        assertEquals("Member", form.inputOf(field(2)))
    }

    @Test
    fun `連動を戻すと連動元の値で上書きする`() {
        var form = inputField(checked(0, 1), list, field(0), "User")
        form = inputField(form, list, field(1), "Admin")
        form = relinkField(form, list, field(1))
        assertEquals("User", form.inputOf(field(1)))
        assertFalse(isUnlinked(field(1), form))
        assertTrue(isLinked(field(1), list, form))
    }

    @Test
    fun `後からチェックした行の空の同名欄は連動元の値で埋まる`() {
        var form = inputField(checked(0), list, field(0), "User")
        form = toggleCheck(form, list, service)
        assertEquals("User", form.inputOf(field(1)))
        assertTrue(isLinked(field(1), list, form))
    }

    @Test
    fun `後からチェックした行が入力済みで値が違えば連動しない`() {
        var form = toggleCheck(FormState(), list, service)
        form = inputField(form, list, field(1), "Admin")
        form = toggleCheck(form, list, service)
        form = toggleCheck(form, list, repository)
        form = inputField(form, list, field(0), "User")
        form = toggleCheck(form, list, service)
        assertEquals("Admin", form.inputOf(field(1)))
        assertTrue(isUnlinked(field(1), form))
    }

    @Test
    fun `チェックを外した行は伝播を受けない`() {
        var form = checked(0, 1)
        form = toggleCheck(form, list, service)
        form = inputField(form, list, field(0), "User")
        assertEquals(null, form.inputOf(field(1)))
    }

    @Test
    fun `どの行から打ち始めてもその欄が連動元になる`() {
        val form = inputField(checked(0, 1, 2), list, field(2), "Order")
        assertEquals(listOf("Order", "Order", "Order"), (0..2).map { form.inputOf(field(it)) })
    }

    @Test
    fun `型が違う同名欄は連動しない`() {
        val a = rowsOf("arch-a", module(":arch-a"))
        val b = rowsOf("arch-b", module(":arch-b"))
        val all = a + b
        val stringName = FieldId(a.row("data.Repository").id, "name")
        val intName = FieldId(b.row("data.Repository").id, "name")
        var form = toggleCheck(FormState(), all, stringName.templateId)
        form = toggleCheck(form, all, intName.templateId)
        form = inputField(form, all, stringName, "User")
        assertEquals(null, form.inputOf(intName))
        assertFalse(isLinked(stringName, all, form))
    }

    @Test
    fun `モジュールが違っても同名同型なら連動する`() {
        val a = rows(template("data.Repository"), module = module(":arch-a"))
        val b = rows(template("data.Repository"), module = module(":arch-b"))
        val all = a + b
        var form = toggleCheck(FormState(), all, a[0].id)
        form = toggleCheck(form, all, b[0].id)
        form = inputField(form, all, FieldId(a[0].id, "name"), "User")
        assertEquals("User", form.inputOf(FieldId(b[0].id, "name")))
    }

    @Test
    fun `連動元のチェックを外したら次に打った欄が連動元になる`() {
        var form = inputField(checked(0, 1, 2), list, field(0), "User")
        form = toggleCheck(form, list, repository)
        form = inputField(form, list, field(2), "Order")
        assertEquals("Order", form.inputOf(field(1)))
        assertFalse(isUnlinked(field(2), form))
        assertEquals(useCase, field(2).templateId)
    }
}
