package me.tbsten.katachi.intellij.data.script

import junit.framework.TestCase
import java.nio.file.Path
import kotlin.io.path.Path

/** How the output of `katachi internal script-classpath` is read (KATACHI-101): every way it can go wrong ends in an answer, not an exception. */
internal class ScriptClasspathInterpreterTest : TestCase() {
    private val existing = setOf(Path("/opt/katachi/katachi-script.jar"), Path("/opt/katachi/katachi.jar"))

    private fun interpret(exitCode: Int?, stdout: String, stderr: String = ""): ScriptClasspath =
        ScriptClasspathInterpreter.interpret(ProcessOutcome(exitCode, stdout, stderr)) { it in existing }

    fun `test 1 行に 1 つずつ並んだ jar は Found になる`() {
        assertEquals(
            ScriptClasspath.Found(existing.toList()),
            interpret(0, "/opt/katachi/katachi-script.jar\n/opt/katachi/katachi.jar\n"),
        )
    }

    fun `test 前後の空白と空行は読み飛ばす`() {
        assertEquals(ScriptClasspath.Found(listOf(Path("/opt/katachi/katachi.jar"))), interpret(0, "\n  /opt/katachi/katachi.jar  \r\n\n"))
    }

    fun `test 0 以外で終わったのは script-classpath を知らない古い CLI`() {
        val answer = interpret(1, "", "Unknown command: internal\nusage: ...")
        assertTrue("$answer", answer is ScriptClasspath.Unsupported)
        assertTrue("the first line of stderr is kept: $answer", "Unknown command: internal" in answer.toString())
    }

    fun `test 終わらなかったのは Malformed`() {
        assertTrue(interpret(null, "") is ScriptClasspath.Malformed)
    }

    fun `test 何も出さなかったのは Malformed`() {
        assertTrue(interpret(0, "\n\n") is ScriptClasspath.Malformed)
    }

    fun `test 無いファイルや相対パスや文章が混じったのは Malformed`() {
        for (stdout in listOf("/opt/katachi/missing.jar", "katachi.jar", "katachi 0.3.0", "/opt/katachi/katachi.jar\nWarning: something")) {
            val answer = interpret(0, stdout)
            assertTrue("$stdout -> $answer", answer is ScriptClasspath.Malformed)
        }
    }

    fun `test パスとして読めない行も例外にせず Malformed`() {
        val answer = ScriptClasspathInterpreter.interpret(ProcessOutcome(0, "/opt/\u0000bad.jar", "")) { _: Path -> true }
        assertTrue("$answer", answer is ScriptClasspath.Malformed)
    }

    fun `test katachi kts で終わる名前だけがスクリプト`() {
        assertTrue(isKatachiScript("setup.katachi.kts"))
        assertFalse(isKatachiScript("build.gradle.kts"))
        assertFalse(isKatachiScript("katachi.main.kts"))
        assertFalse(isKatachiScript("setup.katachi.kt"))
    }
}
