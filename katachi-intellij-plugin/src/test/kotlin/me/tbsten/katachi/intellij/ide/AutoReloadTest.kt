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

/** The automatic reload's waiting (design 4.8): the delay, Gradle, the tool window's own load, and a refusal. */
class AutoReloadTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val state = MutableStateFlow(KatachiScreenState())
    private val activity = GradleActivity()

    @After
    fun tearDown() {
        scope.cancel()
    }

    private suspend fun waitUntil(what: String, condition: () -> Boolean) = withTimeout(5_000) {
        while (!condition()) delay(10)
    }.also { check(condition()) { what } }

    @Test
    fun `待ってから一度だけ読み直し 続けて予約すれば最後からやり直す`() = runBlocking {
        val calls = AtomicInteger()
        val autoReload = AutoReload(scope, state, activity, delayMillis = 150) { calls.incrementAndGet(); true }
        autoReload.schedule()
        delay(100)
        autoReload.schedule()
        delay(100)
        assertEquals(0, calls.get())
        waitUntil("reloaded") { calls.get() == 1 }
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
