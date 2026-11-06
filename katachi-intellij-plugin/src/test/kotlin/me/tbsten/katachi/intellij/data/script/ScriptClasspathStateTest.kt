package me.tbsten.katachi.intellij.data.script

import junit.framework.TestCase
import kotlin.io.path.Path

/** The answer of the CLI as the plugin keeps it (KATACHI-101): asked once, again on request, and never waited for. */
internal class ScriptClasspathStateTest : TestCase() {
    private val found = ScriptClasspath.Found(listOf(Path("/opt/katachi/katachi-script.jar")))
    private val changes = mutableListOf<Pair<ScriptClasspath?, ScriptClasspath>>()
    private var probes = 0

    private fun state(inBackground: (() -> Unit) -> Unit = { it() }, probe: () -> ScriptClasspath) =
        ScriptClasspathState({ probes++; probe() }, inBackground) { before, after -> changes += before to after }

    fun `test 最初に聞かれたときに 1 度だけ CLI に聞く`() {
        val state = state { found }
        assertEquals(found, state.current())
        assertEquals(found, state.current())
        assertEquals(1, probes)
        assertEquals(listOf<Pair<ScriptClasspath?, ScriptClasspath>>(null to found), changes)
    }

    fun `test 答えが出るまでは待たずに null を返す`() {
        val pending = mutableListOf<() -> Unit>()
        val state = state(inBackground = { pending += it }) { found }
        assertNull(state.current())
        assertNull("a second ask does not start a second process", state.current())
        assertEquals(1, pending.size)
        pending.single()()
        assertEquals(found, state.current())
    }

    fun `test もう一度確かめて答えが変わったら知らせる 同じなら知らせない`() {
        var answer: ScriptClasspath = ScriptClasspath.CliNotFound
        val state = state { answer }
        state.current()
        state.recheck()
        answer = found
        state.recheck()
        assertEquals(listOf(null to ScriptClasspath.CliNotFound, ScriptClasspath.CliNotFound to found), changes)
        assertEquals(found, state.current())
    }

    fun `test 聞く途中の例外は Malformed になり 次に確かめることもできる`() {
        var fail = true
        val state = state { if (fail) error("broken") else found }
        val answer = state.current()
        assertTrue("$answer", answer is ScriptClasspath.Malformed)
        fail = false
        state.recheck()
        assertEquals(found, state.current())
    }

    fun `test jar に定義が無かった Found は Malformed に置き換える ほかの答えが来ていれば置き換えない`() {
        val state = state { found }
        state.current()
        val broken = ScriptClasspath.Malformed("no definition")
        state.replace(found, broken)
        assertEquals(broken, state.current())
        state.replace(found, ScriptClasspath.CliNotFound)
        assertEquals(broken, state.current())
        assertEquals(found to broken, changes.last())
    }
}
