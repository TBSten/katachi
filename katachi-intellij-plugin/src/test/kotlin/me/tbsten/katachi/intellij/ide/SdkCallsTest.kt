package me.tbsten.katachi.intellij.ide

import com.intellij.openapi.progress.ProcessCanceledException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.coroutines.cancellation.CancellationException

/** [sdkCall] catches what an SDK call throws, except what must reach the caller. */
class SdkCallsTest {
    @Test
    fun `成功した呼び出しは値をそのまま返す`() {
        assertEquals(Result.success(42), sdkCall("answer") { 42 })
    }

    @Test
    fun `ふつうの例外は握って失敗のResultにする`() {
        val thrown = IllegalStateException("the platform said no")
        val result = sdkCall("ask the platform") { throw thrown }
        assertTrue(result.isFailure)
        assertSame(thrown, result.exceptionOrNull())
    }

    @Test
    fun `ProcessCanceledExceptionは握らずに投げ直す`() {
        assertRethrown(ProcessCanceledException())
    }

    @Test
    fun `コルーチンのCancellationExceptionは握らずに投げ直す`() {
        assertRethrown(CancellationException("cancelled"))
    }

    @Test
    fun `OutOfMemoryErrorは握らずに投げ直す`() {
        assertRethrown(OutOfMemoryError("no memory"))
    }

    @Test
    fun `LinkageErrorとInterruptedExceptionとAssertionErrorは握らずに投げ直す`() {
        assertRethrown(NoClassDefFoundError("missing"))
        assertRethrown(InterruptedException("stop"))
        assertRethrown(AssertionError("result"))
    }

    private fun assertRethrown(failure: Throwable) {
        val caught = runCatching { sdkCall("run") { throw failure } }.exceptionOrNull()
        assertSame(failure, caught)
    }
}
