package me.tbsten.katachi.intellij.presentation

import me.tbsten.katachi.intellij.testing.booleanParam
import me.tbsten.katachi.intellij.testing.branch
import me.tbsten.katachi.intellij.testing.enumParam
import me.tbsten.katachi.intellij.testing.row
import me.tbsten.katachi.intellij.testing.rowsOf
import me.tbsten.katachi.intellij.testing.stringParam
import me.tbsten.katachi.intellij.testing.template
import org.junit.Assert.assertEquals
import org.junit.Test

class ParameterVisibilityTest {
    private val rows = rowsOf("arch-a")
    private val repository = rows.row("data.Repository").template.detail ?: throw AssertionError()
    private val useCase = rows.row("domain.UseCase").template.detail ?: throw AssertionError()

    private fun describe(slots: List<FieldSlot>) = slots.map {
        when (it) {
            is FieldSlot.Shown -> it.parameter.name
            is FieldSlot.Collapsed -> "(${it.parameter.name} when ${it.controllerName}=${it.whenValue})"
        }
    }

    @Test
    fun `プレビュー値のままなら全部の欄を宣言順に出す`() {
        assertEquals(listOf("name", "item", "withImpl", "implSuffix"), describe(fieldSlotsOf(repository, emptyMap())))
    }

    @Test
    fun `分岐で消える欄は制御するパラメータの値を添えて畳む`() {
        assertEquals(
            listOf("name", "item", "withImpl", "(implSuffix when withImpl=true)"),
            describe(fieldSlotsOf(repository, mapOf("withImpl" to "false"))),
        )
    }

    @Test
    fun `畳んでも入力は残りオンに戻すと欄が戻る`() {
        val inputs = mapOf("withImpl" to "false", "implSuffix" to "Default")
        assertEquals(listOf("name", "item", "withImpl"), shownParametersOf(repository, inputs).map { it.name })
        assertEquals("implSuffix", shownParametersOf(repository, inputs + ("withImpl" to "true")).last().name)
    }

    @Test
    fun `既定オフの分岐で増える欄は制御するパラメータの直後に畳んで出し値が合えば出す`() {
        assertEquals(
            listOf("name", "visibility", "retries", "withTest", "(testRunner when withTest=true)"),
            describe(fieldSlotsOf(useCase, emptyMap())),
        )
        assertEquals(
            listOf("name", "visibility", "retries", "withTest", "testRunner"),
            describe(fieldSlotsOf(useCase, mapOf("withTest" to "true"))),
        )
    }

    @Test
    fun `enumの値ごとに違う欄が増えるときは選んだ値の欄だけ出す`() {
        val detail = template(
            "a/X",
            parameters = listOf(enumParam("style", listOf("Plain", "Fancy", "Loud")), stringParam("name")),
            branches = listOf(
                branch("style", "Fancy", addedParameters = listOf(stringParam("ribbon", "red"))),
                branch("style", "Loud", addedParameters = listOf(stringParam("volume", "11"))),
            ),
        ).detail ?: throw AssertionError()
        assertEquals(
            listOf("style", "ribbon", "(volume when style=Loud)", "name"),
            describe(fieldSlotsOf(detail, mapOf("style" to "Fancy"))),
        )
    }

    @Test
    fun `空の欄は既定値で分岐を決める`() {
        val detail = template(
            "a/X",
            parameters = listOf(booleanParam("flag", default = "false")),
            branches = listOf(branch("flag", "true", addedParameters = listOf(stringParam("extra")))),
        ).detail ?: throw AssertionError()
        assertEquals(listOf("flag", "(extra when flag=true)"), describe(fieldSlotsOf(detail, mapOf("flag" to ""))))
    }

    @Test
    fun `分岐の値と一致するものだけを有効な分岐にする`() {
        assertEquals(emptyList<Any>(), activeBranchesOf(repository, mapOf("withImpl" to "true")))
        assertEquals(listOf("false"), activeBranchesOf(repository, mapOf("withImpl" to "false")).map { it.value })
    }
}
