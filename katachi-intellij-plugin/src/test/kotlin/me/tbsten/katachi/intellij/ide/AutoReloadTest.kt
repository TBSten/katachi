package me.tbsten.katachi.intellij.ide

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.tbsten.katachi.intellij.data.gradle.GradleActivity
import me.tbsten.katachi.intellij.presentation.KatachiScreenState
import me.tbsten.katachi.intellij.presentation.LoadingState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds

/** The automatic reload's waiting (design 4.8): the delay, Gradle, the tool window's own load, and a refusal. */
class AutoReloadTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val state = MutableStateFlow(KatachiScreenState())
    private val activity = GradleActivity()

    @After
    fun tearDown() {
        scope.cancel()
    }

    private companion object {
        // Wall-clock margins are what a slow CI runner breaks: the second schedule() must land inside the first delay, so the delay
        // is long against the 100 ms between them, and nothing asserts "not reloaded yet" at a point in time.
        const val DELAY_MILLIS: Long = 1_000L
    }

    private suspend fun waitUntil(what: String, condition: () -> Boolean) = withTimeout(5_000) {
        while (!condition()) delay(10)
    }.also { check(condition()) { what } }

    @Test
    fun `待ってから一度だけ読み直し 続けて予約すれば最後からやり直す`() = runBlocking {
        val calls = AtomicInteger()
        val reloadedAt = AtomicLong()
        val autoReload = AutoReload(scope, state, activity, delayMillis = DELAY_MILLIS) {
            reloadedAt.set(System.nanoTime())
            calls.incrementAndGet()
            true
        }
        autoReload.schedule()
        delay(100)
        val rescheduledAt = System.nanoTime()
        autoReload.schedule()
        waitUntil("reloaded") { calls.get() == 1 }
        // A reload of the first schedule() would come before the second one's delay is over (or before it at all).
        val waited = (reloadedAt.get() - rescheduledAt).nanoseconds
        assertTrue("reloaded $waited after the second schedule(), less than ${DELAY_MILLIS.milliseconds}", waited >= DELAY_MILLIS.milliseconds)
        delay(300)
        assertEquals(1, calls.get())
        assertFalse(autoReload.isPending)
    }

    @Test
    fun `Gradle が走っている間と自分の読み込みが走っている間は待つ`() = runBlocking {
        val calls = AtomicInteger()
        val autoReload = AutoReload(scope, state, activity, delayMillis = 20) { calls.incrementAndGet(); true }
        activity.externalStarted("sync")
        state.value = state.value.copy(loading = LoadingState(isInitial = false))
        autoReload.schedule()
        delay(300)
        assertEquals(0, calls.get())
        assertTrue(autoReload.isPending)
        activity.externalEnded("sync")
        delay(100)
        assertEquals(0, calls.get())
        state.value = state.value.copy(loading = null)
        waitUntil("reloaded") { calls.get() == 1 }
    }

    @Test
    fun `読み直しを断られたら失わずに少し待って聞き直す`() = runBlocking {
        // The ViewModel refuses while its load job is finishing: loading is null already, the job is not done yet.
        val calls = AtomicInteger()
        val autoReload = AutoReload(scope, state, activity, delayMillis = 20) { calls.incrementAndGet() >= 3 }
        autoReload.schedule()
        waitUntil("asked three times") { calls.get() == 3 }
        delay(300)
        assertEquals(3, calls.get())
        assertFalse(autoReload.isPending)
    }
}
