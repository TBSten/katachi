package me.tbsten.katachi.intellij.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EchoTrackerTest {
    @Test
    fun `自分が送った値が遅れて戻っても反響として書き戻さない`() {
        val echoes = EchoTracker()
        echoes.sent("U")
        echoes.sent("Us")
        // "U" comes back while the field already holds "Us".
        assertTrue(echoes.isEcho("U"))
        assertTrue(echoes.isEcho("Us"))
    }

    @Test
    fun `打ち戻しで同じ値を2回送っても順に反響として扱う`() {
        val echoes = EchoTracker()
        echoes.sent("a")
        echoes.sent("ab")
        echoes.sent("a")
        assertTrue(echoes.isEcho("a"))
        assertTrue(echoes.isEcho("ab"))
        assertTrue(echoes.isEcho("a"))
    }

    @Test
    fun `送っていない値は外からの変更として書き戻す`() {
        val echoes = EchoTracker()
        echoes.sent("User")
        assertFalse(echoes.isEcho(""))
        // The pending ones are dropped: the outside value replaced them.
        assertFalse(echoes.isEcho("User"))
    }
}
