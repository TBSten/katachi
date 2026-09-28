package me.tbsten.katachi.intellij.presentation

import me.tbsten.katachi.intellij.model.allParametersOf
import me.tbsten.katachi.intellij.testing.ContractFixtures
import me.tbsten.katachi.intellij.testing.row
import me.tbsten.katachi.intellij.testing.rowsOf
import me.tbsten.katachi.intellij.testing.stringParam
import me.tbsten.katachi.intellij.testing.template
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaceholdersTest {
    private val repository = rowsOf("arch-a").row("data.Repository").template.detail ?: throw AssertionError()

    @Test
    fun `入力値でプレースホルダを置き換える`() {
        assertEquals("UserRepository.kt", expectedTextOf("\${name}Repository.kt", repository, mapOf("name" to "User")))
    }

    @Test
    fun `未入力ならプレースホルダのまま残す`() {
        assertEquals("\${name}Repository.kt", expectedTextOf("\${name}Repository.kt", repository, emptyMap()))
        assertEquals("\${name}Repository.kt", expectedTextOf("\${name}Repository.kt", repository, mapOf("name" to "  ")))
    }

    @Test
    fun `空の欄は既定値で置き換える`() {
        assertEquals("UserRepositoryImpl.kt", expectedTextOf("\${name}Repository\${implSuffix}.kt", repository, mapOf("name" to "User")))
    }

    @Test
    fun `他のパラメータを読む既定値は入力に合わせて置き換える`() {
        val service = ContractFixtures.templates("sample-jvm").single().detail ?: throw AssertionError()
        val kdoc = service.parameters.single { it.name == "kdoc" }
        // "name" is a capture, not a parameter (design draft: name moved off `Service`'s parameters
        // and onto the layout's `capture("name")`), so the lookup default reads from must include
        // captures too -- exactly what `allParametersOf` gives a processor's own capture reads.
        val parameters = allParametersOf(service).associateBy { it.name }
        assertEquals("Order に関するアプリ固有の振る舞い。", expectedValueOf(kdoc, mapOf("name" to "Order"), parameters))
        assertEquals("\${name} に関するアプリ固有の振る舞い。", expectedValueOf(kdoc, emptyMap(), parameters))
    }

    @Test
    fun `既定値の置き換えは1段だけで循環しても止まる`() {
        val detail = template("a/X", parameters = listOf(stringParam("a", "\${b}"), stringParam("b", "\${a}"))).detail ?: throw AssertionError()
        assertEquals("\${b}-\${a}", expectedTextOf("\${a}-\${b}", detail, emptyMap()))
    }

    @Test
    fun `加工されたプレースホルダは加工前の値のまま出る`() {
        // katachi previews `${name.lowercase()}` as `${name}`: the expectation is knowingly off (E-23).
        assertEquals("Reads User data.", expectedTextOf("Reads \${name} data.", repository, mapOf("name" to "User")))
        assertEquals("\${name.lowercase()}", expectedTextOf("\${name.lowercase()}", repository, mapOf("name" to "User")))
    }

    @Test
    fun `Booleanは入力が無ければ既定値を使う`() {
        val withImpl = repository.parameters.single { it.name == "withImpl" }
        assertEquals("true", expectedValueOf(withImpl, emptyMap(), emptyMap()))
        assertEquals("false", expectedValueOf(withImpl, mapOf("withImpl" to "false"), emptyMap()))
    }
}
