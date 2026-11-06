package me.tbsten.katachi.intellij.data.script

import junit.framework.TestCase
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText

/**
 * The probe against real processes (KATACHI-101): stand-ins for `katachi` written as shell scripts, one per way an
 * installed CLI can answer. On Windows, which has no `sh`, the tests return without checking.
 */
internal class ScriptClasspathProbeTest : TestCase() {
    private val windows = System.getProperty("os.name").orEmpty().startsWith("Windows")
    private lateinit var dir: Path

    override fun setUp() {
        super.setUp()
        dir = createTempDirectory("katachi-cli-")
    }

    override fun tearDown() {
        try {
            dir.toFile().deleteRecursively()
        } finally {
            super.tearDown()
        }
    }

    private fun fakeCli(body: String): Path {
        val cli = dir.resolve("katachi")
        cli.writeText("#!/bin/sh\n$body\n")
        cli.toFile().setExecutable(true)
        return cli
    }

    private fun probe(cli: Path?, timeoutSeconds: Long = 30): ScriptClasspath =
        ScriptClasspathProbe(locate = { cli }, run = TimedProcessRunner(timeoutSeconds), exists = Files::exists).probe()

    fun `test jar を並べて答える CLI からは Found`() {
        if (windows) return
        val jar = dir.resolve("katachi-script.jar").also { it.writeText("") }
        val cli = fakeCli("""[ "$1 $2" = "internal script-classpath" ] || exit 2; echo "$jar"""")
        assertEquals(ScriptClasspath.Found(listOf(jar)), probe(cli))
    }

    fun `test CLI が無ければ CliNotFound で プロセスは起こさない`() {
        assertEquals(ScriptClasspath.CliNotFound, probe(null))
    }

    fun `test サブコマンドを知らない古い CLI からは Unsupported`() {
        if (windows) return
        val cli = fakeCli("echo 'Unknown command: internal' >&2; exit 1")
        assertTrue(probe(cli) is ScriptClasspath.Unsupported)
    }

    fun `test 壊れた答えからは Malformed`() {
        if (windows) return
        assertTrue(probe(fakeCli("echo 'katachi 0.3.0'")) is ScriptClasspath.Malformed)
    }

    fun `test 終わらない CLI は待ち時間で打ち切って Malformed`() {
        if (windows) return
        val started = System.nanoTime()
        assertTrue(probe(fakeCli("sleep 30"), timeoutSeconds = 1) is ScriptClasspath.Malformed)
        assertTrue("gave up after the timeout", System.nanoTime() - started < 20_000_000_000L)
    }

    fun `test 起動できないファイルは例外にせず Malformed`() {
        if (windows) return
        val notExecutable = dir.resolve("katachi").also { it.writeText("not a program") }
        assertTrue(probe(notExecutable) is ScriptClasspath.Malformed)
    }

    fun `test 出力が多くても詰まらずに読み切る`() {
        if (windows) return
        val jar = dir.resolve("big.jar").also { it.writeText("") }
        // More than a pipe holds on stderr, before the answer on stdout.
        val cli = fakeCli("i=0; while [ ${'$'}i -lt 2000 ]; do echo 'noise noise noise noise noise noise noise noise' >&2; i=${'$'}((i+1)); done; echo \"$jar\"")
        assertEquals(ScriptClasspath.Found(listOf(jar)), probe(cli))
    }
}
