package me.tbsten.katachi.intellij.data.script

import junit.framework.TestCase
import java.nio.file.Path
import kotlin.io.path.Path

/** When a `*.katachi.kts` gets a ▶, and what it runs (KATACHI-100). */
internal class ScriptRunTest : TestCase() {
    private val found = ScriptClasspath.Found(listOf(Path("/opt/katachi/katachi-script.jar")))

    fun `test CLI が定義を渡した katachi kts だけが実行できる`() {
        assertTrue(ScriptRun.runnable("setup.katachi.kts", found))
    }

    fun `test CLI が無い 古い 壊れた 答えを待っている のどれでも実行できない`() {
        for (answer in listOf(null, ScriptClasspath.CliNotFound, ScriptClasspath.Unsupported("exit 1"), ScriptClasspath.Malformed("not a file"))) {
            assertFalse("$answer", ScriptRun.runnable("setup.katachi.kts", answer))
        }
    }

    fun `test katachi kts 以外のファイルは CLI があっても実行できない`() {
        for (name in listOf("build.gradle.kts", "katachi.main.kts", "Main.kt", "katachi.kts.txt")) {
            assertFalse(name, ScriptRun.runnable(name, found))
        }
    }

    fun `test コマンドは terminal で katachi file katachi kts と打つのと同じ`() {
        assertEquals(listOf("/opt/homebrew/bin/katachi", "/p/setup.katachi.kts"), ScriptRun.command(Path("/opt/homebrew/bin/katachi"), "/p/setup.katachi.kts"))
    }

    fun `test 実行する場所はプロジェクトの場所で 無ければスクリプトのあるディレクトリ`() {
        assertEquals("/p", ScriptRun.workingDirectory("/p", "/p/scripts/setup.katachi.kts"))
        assertEquals("/p/scripts", ScriptRun.workingDirectory(null, "/p/scripts/setup.katachi.kts"))
        assertNull(ScriptRun.workingDirectory(null, "setup.katachi.kts"))
    }
}
