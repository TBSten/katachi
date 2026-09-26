package me.tbsten.katachi.intellij.presentation

import me.tbsten.katachi.intellij.testing.booleanParam
import me.tbsten.katachi.intellij.testing.enumParam
import me.tbsten.katachi.intellij.testing.intParam
import me.tbsten.katachi.intellij.testing.stringParam
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ParameterValidationTest {
    private val requiredInt = intParam("count")
    private val optionalInt = intParam("count", default = "3")

    @Test
    fun `整数でない入力はエラーにする`() {
        for (input in listOf("abc", "1.5", "99999999999", "-2147483649", "1 2", "１２", "0x10", "-", "+")) {
            assertEquals(input, FieldError.NotAnInt(), validateField(requiredInt, input))
        }
    }

    @Test
    fun `前後の空白とマイナスゼロと範囲の端は受け付ける`() {
        for (input in listOf(" 42 ", "-0", "+7", "007", "2147483647", "-2147483648")) {
            assertNull(input, validateField(requiredInt, input))
        }
    }

    @Test
    fun `送る前に整数を正規化する`() {
        assertEquals("0", normalizedInt("-0"))
        assertEquals("42", normalizedInt(" 42 "))
        assertEquals("7", normalizedInt("+007"))
        assertNull(normalizedInt("１２"))
        assertEquals("0", argValueOf(requiredInt, " -0"))
    }

    @Test
    fun `必須のStringとIntとenumが空ならエラーにする`() {
        assertEquals(FieldError.Required, validateField(stringParam("name"), null))
        assertEquals(FieldError.Required, validateField(stringParam("name"), "   "))
        assertEquals(FieldError.Required, validateField(requiredInt, ""))
        assertEquals(FieldError.Required, validateField(enumParam("v", listOf("A", "B"), default = null), null))
    }

    @Test
    fun `既定値のある欄は空でもよい`() {
        assertNull(validateField(stringParam("item", "String"), ""))
        assertNull(validateField(optionalInt, null))
        assertNull(validateField(enumParam("v", listOf("A", "B"), default = "A"), ""))
    }

    @Test
    fun `enumの候補外はエラーにし大文字小文字も区別する`() {
        val visibility = enumParam("visibility", listOf("Public", "Internal"))
        assertNull(validateField(visibility, "Internal"))
        assertEquals(FieldError.NotAcceptedValue("internal", listOf("Public", "Internal")), validateField(visibility, "internal"))
    }

    @Test
    fun `Booleanは必ず値を持つのでエラーにならない`() {
        assertNull(validateField(booleanParam("flag", default = null), null))
    }

    @Test
    fun `Stringは文字種を検証しない`() {
        assertNull(validateField(stringParam("name"), "a/../b:\\c\u0000"))
    }
}
