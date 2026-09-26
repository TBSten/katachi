package me.tbsten.katachi.intellij.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The search field and the text fields against the UI state, as sequences of what the field
 * reports and what the state sends back. The state arrives a frame late, so several edits can
 * happen before the first echo comes back.
 */
class TextFieldSyncTest {
    /** A field wired to a state holder, recording what reached the state. */
    private class Field(initial: String = "") {
        val sync = TextFieldSync(initial)
        var text = initial
        val sent = mutableListOf<String>()

        fun type(next: String) {
            text = next
            if (sync.onTyped(next)) sent += next
        }

        /** The state's value reaches the field; the field writes it in when told to. */
        fun receive(value: String) {
            sync.onValue(value, text)?.let { written ->
                text = written
                // Writing into the field makes it report its text again.
                if (sync.onTyped(written)) sent += written
            }
        }
    }

    @Test
    fun `1文字打ってすぐ消すと状態が値を返す前でも空に戻したことを送る`() {
        val field = Field()
        field.type("x")
        field.type("")
        assertEquals(listOf("x", ""), field.sent)

        field.receive("x")
        field.receive("")
        assertEquals("", field.text)
    }

    @Test
    fun `1文字足してすぐ消しても元の値に戻したことを送る`() {
        val field = Field("repo")
        field.type("repos")
        field.type("repo")
        assertEquals(listOf("repos", "repo"), field.sent)
        field.receive("repos")
        field.receive("repo")
        assertEquals("repo", field.text)
    }

    @Test
    fun `連打で1文字ずつ消して空にすると最後の空まで送り途中の値で書き戻さない`() {
        val field = Field()
        "repo".indices.forEach { field.type("repo".substring(0, it + 1)) }
        listOf("rep", "re", "r", "").forEach(field::type)
        // The state only now catches up, and with an old value first.
        field.receive("rep")
        assertEquals("", field.text)
        field.receive("")
        assertEquals("", field.text)
        assertEquals("", field.sent.last())
    }

    @Test
    fun `外から空にされると欄も空になりその書き込みを送り返さない`() {
        val field = Field()
        field.type("User")
        field.receive("User")
        field.receive("")
        assertEquals("", field.text)
        assertEquals(listOf("User"), field.sent)

        field.type("Order")
        assertEquals(listOf("User", "Order"), field.sent)
    }

    @Test
    fun `外からの値が同じ文字でも送った値の返りでなければ次の入力を受け付ける`() {
        val field = Field()
        field.type("a")
        field.receive("a")
        field.receive("b")
        assertEquals("b", field.text)
        field.type("a")
        assertEquals(listOf("a", "a"), field.sent)
    }

    @Test
    fun `前後の空白や空白だけも打ったとおりに送り書き戻さない`() {
        val field = Field()
        field.type(" ")
        field.type("  x ")
        field.receive(" ")
        assertEquals("  x ", field.text)
        field.receive("  x ")
        assertEquals(listOf(" ", "  x "), field.sent)
    }

    @Test
    fun `返ってきた値と同じ文字をもう一度打っても送らない`() {
        val sync = TextFieldSync("a")
        assertFalse(sync.onTyped("a"))
        assertTrue(sync.onTyped("ab"))
        assertNull(sync.onValue("ab", "ab"))
        assertFalse(sync.onTyped("ab"))
    }
}
