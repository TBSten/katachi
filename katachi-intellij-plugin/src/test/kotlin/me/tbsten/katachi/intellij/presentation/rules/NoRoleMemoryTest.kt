package me.tbsten.katachi.intellij.presentation.rules

import me.tbsten.katachi.intellij.presentation.entry.EditorNotificationMemory
import me.tbsten.katachi.intellij.testing.underRoot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** The × of the no-role notification is remembered apart from the template notification's, per file. */
class NoRoleMemoryTest {
    private val a = underRoot("src/A.kt")
    private val b = underRoot("src/B.kt")

    @Test
    fun `当たる Role 無しの × はファイルごとに覚えテンプレートの × とは別に数える`() {
        val memory = EditorNotificationMemory.EMPTY.dismissNoRole(a)

        assertTrue(memory.isNoRoleDismissed(a))
        assertFalse(memory.isNoRoleDismissed(b))
        assertFalse("the template notification's x is its own", memory.isDismissed(a))
        assertFalse(EditorNotificationMemory.EMPTY.dismiss(a).isNoRoleDismissed(a))
    }

    @Test
    fun `閉じるのは新しい値を返し同じ閉じ方を重ねても同じ値で順序によらず等しい`() {
        val before = EditorNotificationMemory.EMPTY
        val after = before.dismissNoRole(a)

        assertFalse(before.isNoRoleDismissed(a))
        assertNotEquals(before, after)
        assertSame(after, after.dismissNoRole(a))
        assertEquals(before.dismissNoRole(a).dismiss(b), before.dismiss(b).dismissNoRole(a))
        assertEquals(before.dismissNoRole(a).dismiss(b).hashCode(), before.dismiss(b).dismissNoRole(a).hashCode())
        assertNotEquals(before.dismissNoRole(a), before.dismiss(a))
    }
}
