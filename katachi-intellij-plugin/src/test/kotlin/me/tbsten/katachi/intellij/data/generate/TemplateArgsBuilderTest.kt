package me.tbsten.katachi.intellij.data.generate

import me.tbsten.katachi.intellij.presentation.OnExistingChoice
import me.tbsten.katachi.intellij.testing.ContractFixtures
import me.tbsten.katachi.intellij.testing.booleanParam
import me.tbsten.katachi.intellij.testing.module
import me.tbsten.katachi.intellij.testing.row
import me.tbsten.katachi.intellij.testing.rowsOf
import me.tbsten.katachi.intellij.testing.template
import org.junit.Assert.assertEquals
import org.junit.Test

class TemplateArgsBuilderTest {
    private val rows = rowsOf("arch-a")
    private val repository = rows.row("data/Repository").template.detail ?: throw AssertionError()
    private val useCase = rows.row("domain/UseCase").template.detail ?: throw AssertionError()

    @Test
    fun `roleNameは修飾名でonExistingと一緒に常に先頭に入る`() {
        val args = templateArgsOf("data/Repository", repository, mapOf("name" to "User"), OnExistingChoice.Fail)
        assertEquals(listOf("roleName" to "data/Repository", "onExisting" to "fail", "name" to "User"), args)
    }

    @Test
    fun `onExistingは3つの値をそのままの綴りで送る`() {
        val values = OnExistingChoice.entries.map { choice ->
            templateArgsOf("data/Repository", repository, mapOf("name" to "User"), choice).first { it.first == "onExisting" }.second
        }
        assertEquals(listOf("fail", "skip", "overwrite"), values)
    }

    @Test
    fun `空で既定値のある欄は送らない`() {
        val args = templateArgsOf("data/Repository", repository, mapOf("name" to "User", "item" to "", "implSuffix" to "  "), OnExistingChoice.Fail)
        assertEquals(listOf("roleName", "onExisting", "name"), args.map { it.first })
    }

    @Test
    fun `既定値が他のパラメータを読む欄も空なら送らずkatachiに評価させる`() {
        val label = rows.row("misc/Label").template.detail ?: throw AssertionError()
        val args = templateArgsOf("misc/Label", label, mapOf("name" to "User"), OnExistingChoice.Fail)
        assertEquals(listOf("roleName", "onExisting", "name"), args.map { it.first })
    }

    @Test
    fun `畳んだ条件付きパラメータは入力が残っていても送らない`() {
        val inputs = mapOf("name" to "User", "withImpl" to "false", "implSuffix" to "Default")
        val args = templateArgsOf("data/Repository", repository, inputs, OnExistingChoice.Fail)
        assertEquals(listOf("roleName", "onExisting", "name", "withImpl"), args.map { it.first })
    }

    @Test
    fun `分岐で現れたパラメータは値が合えば送る`() {
        val inputs = mapOf("name" to "Login", "withTest" to "true", "testRunner" to "Kotest")
        val args = templateArgsOf("domain/UseCase", useCase, inputs, OnExistingChoice.Fail)
        assertEquals(listOf("name" to "Login", "withTest" to "true", "testRunner" to "Kotest"), args.drop(2))
    }

    @Test
    fun `Booleanとenumは入力の綴りのまま送りIntは正規化する`() {
        val inputs = mapOf("name" to "Login", "visibility" to "Internal", "retries" to " -0 ", "withTest" to "false")
        val args = templateArgsOf("domain/UseCase", useCase, inputs, OnExistingChoice.Fail)
        assertEquals(listOf("name" to "Login", "visibility" to "Internal", "retries" to "0", "withTest" to "false"), args.drop(2))
    }

    @Test
    fun `既定値の無いBooleanは触っていなくてもtrueを送る`() {
        val detail = template("a/X", parameters = listOf(booleanParam("flag", default = null))).detail ?: throw AssertionError()
        assertEquals(listOf("flag" to "true"), templateArgsOf("a/X", detail, emptyMap(), OnExistingChoice.Fail).drop(2))
    }

    @Test
    fun `改行や引用符やイコールを含む値は加工せず1要素で渡す`() {
        val value = "a\nb \"c\" 'd' \\e =f -g 日本語"
        val args = templateArgsOf("data/Repository", repository, mapOf("name" to value), OnExistingChoice.Fail)
        assertEquals("name" to value, args.last())
    }

    @Test
    fun `生成はそのテンプレートのモジュールのkatachiTemplateで実行する`() {
        val invocation = templateInvocationOf(module(":arch-b"), listOf("roleName" to "data/Repository"))
        assertEquals(":arch-b:katachiTemplate", invocation.taskPath)
        assertEquals(":katachiTemplate", templateInvocationOf(module(":"), emptyList()).taskPath)
    }

    @Test
    fun `sample-jvmの実出力からもargsを組める`() {
        val service = ContractFixtures.templates("sample-jvm").single().detail ?: throw AssertionError()
        assertEquals(
            listOf("roleName" to "domain/Service", "onExisting" to "skip", "name" to "Order"),
            templateArgsOf("domain/Service", service, mapOf("name" to "Order"), OnExistingChoice.Skip),
        )
    }
}
