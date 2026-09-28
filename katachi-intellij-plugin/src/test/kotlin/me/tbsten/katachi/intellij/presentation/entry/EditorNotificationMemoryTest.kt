package me.tbsten.katachi.intellij.presentation.entry

import me.tbsten.katachi.intellij.testing.underRoot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorNotificationMemoryTest {
    private val a = underRoot("src/A.kt")
    private val b = underRoot("src/B.kt")

    // covers: 論点22
    @Test
    fun `バツと最初の1回はファイルごとに覚え別のファイルに影響しない`() {
        val memory = EditorNotificationMemory.EMPTY.dismiss(a).markContentNoticeShown(b)
        assertTrue(memory.isDismissed(a))
        assertFalse(memory.isDismissed(b))
        assertTrue(memory.hasShownContentNotice(b))
        assertFalse(memory.hasShownContentNotice(a))
    }

    // covers: 論点15
    @Test
    fun `変更は新しい値を返し元の値は変わらない`() {
        val before = EditorNotificationMemory.EMPTY
        val after = before.dismiss(a)
        assertFalse(before.isDismissed(a))
        assertNotEquals(before, after)
        assertSame(after, after.dismiss(a))
        assertEquals(before.dismiss(a).markContentNoticeShown(a), before.markContentNoticeShown(a).dismiss(a))
    }
}
