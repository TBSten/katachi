package me.tbsten.katachi.intellij.presentation.authoring

import me.tbsten.katachi.intellij.data.definition.CapturedValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A role's file as the body of its template: the value of each capture put back as a reference. */
class TemplateDraftsTest {
    private val name = CapturedValue("name", "User")

    @Test
    fun `the value of a capture becomes a reference to it`() {
        assertEquals("class \${name}Service(val repository: \${name}Repository)", TemplateDrafts.bodyOf("class UserService(val repository: UserRepository)", listOf(name)))
    }

    @Test
    fun `the longest value is taken first`() {
        val body = TemplateDrafts.bodyOf("UserProfile and User", listOf(name, CapturedValue("kind", "UserProfile")))

        assertEquals("\${kind} and \${name}", body)
    }

    @Test
    fun `a replaced reference is not read again`() {
        val body = TemplateDrafts.bodyOf("User x", listOf(name, CapturedValue("other", "name")))

        assertEquals("\${name} x", body)
    }

    @Test
    fun `a dollar in the file is escaped`() {
        assertEquals("val price = \"\${'$'}5\"", TemplateDrafts.bodyOf("val price = \"$5\"", emptyList()))
    }

    @Test
    fun `a value of one letter is not taken for a capture`() {
        assertEquals("val x = 1", TemplateDrafts.bodyOf("val x = 1", listOf(CapturedValue("v", "x"))))
    }

    @Test
    fun `a file with a triple quote cannot be a raw string`() {
        assertNull(TemplateDrafts.bodyOf("val s = \"\"\"a\"\"\"", emptyList()))
    }

    @Test
    fun `a file longer than the limit gives no draft`() {
        assertNull(TemplateDrafts.bodyOf((1..TemplateDrafts.LINES_MAX + 1).joinToString("\n") { "line $it" }, emptyList()))
    }

    @Test
    fun `trailing blank lines are cut`() {
        assertEquals("a\nb", TemplateDrafts.bodyOf("a\nb\n\n", emptyList()))
    }
}
