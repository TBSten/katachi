package me.tbsten.katachi.intellij.data.script

import junit.framework.TestCase
import java.nio.file.Path
import kotlin.io.path.Path

/** Where the plugin looks for `katachi` (KATACHI-101). */
internal class KatachiCliLocatorTest : TestCase() {
    private fun locator(path: String?, executables: Set<Path> = emptySet(), windows: Boolean = false) =
        KatachiCliLocator(pathVariable = path, home = "/Users/me", windows = windows, isExecutable = { it in executables })

    fun `test PATH の順に探し そのあとに brew と local bin を探す`() {
        assertEquals(
            listOf(
                "/a/katachi",
                "/b/katachi",
                "/opt/homebrew/bin/katachi",
                "/usr/local/bin/katachi",
                "/home/linuxbrew/.linuxbrew/bin/katachi",
                "/Users/me/.linuxbrew/bin/katachi",
                "/Users/me/.local/bin/katachi",
            ).map(::Path),
            locator("/a:/b").candidates(),
        )
    }

    fun `test PATH に同じ場所があれば 1 度だけ探す`() {
        assertEquals(1, locator("/opt/homebrew/bin:/opt/homebrew/bin").candidates().count { it == Path("/opt/homebrew/bin/katachi") })
    }

    fun `test IDE が PATH を持たなくても brew の場所で見つかる`() {
        assertEquals(Path("/opt/homebrew/bin/katachi"), locator(null, setOf(Path("/opt/homebrew/bin/katachi"))).locate())
    }

    fun `test PATH にあればそちらが先`() {
        val executables = setOf(Path("/a/katachi"), Path("/opt/homebrew/bin/katachi"))
        assertEquals(Path("/a/katachi"), locator("/a", executables).locate())
    }

    fun `test どこにも無ければ null`() {
        assertNull(locator("/a:/b").locate())
    }

    fun `test katachi に渡す PATH は 探した場所を同じ順に並べたもの`() {
        assertEquals(
            "/a:/opt/homebrew/bin:/usr/local/bin:/home/linuxbrew/.linuxbrew/bin:/Users/me/.linuxbrew/bin:/Users/me/.local/bin",
            locator("/a:/opt/homebrew/bin").searchPath(),
        )
    }

    fun `test IDE が PATH を持たなくても katachi に渡す PATH には brew の場所が入る`() {
        assertEquals(
            "/opt/homebrew/bin:/usr/local/bin:/home/linuxbrew/.linuxbrew/bin:/Users/me/.linuxbrew/bin:/Users/me/.local/bin",
            locator(null).searchPath(),
        )
    }

    fun `test Linux の Homebrew の場所でも見つかる`() {
        assertEquals(
            Path("/home/linuxbrew/.linuxbrew/bin/katachi"),
            locator(null, setOf(Path("/home/linuxbrew/.linuxbrew/bin/katachi"))).locate(),
        )
        assertEquals(Path("/Users/me/.linuxbrew/bin/katachi"), locator(null, setOf(Path("/Users/me/.linuxbrew/bin/katachi"))).locate())
    }

    fun `test Windows では Scoop の shims を PATH のあとに探す`() {
        val candidates = locator("C:\\tools", windows = true).candidates()
        assertEquals(6, candidates.size)
        assertEquals(listOf("katachi.bat", "katachi.cmd", "katachi.exe"), candidates.takeLast(3).map { it.fileName.toString() })
        assertTrue(candidates.takeLast(3).all { it.parent.toString().endsWith("scoop\\shims") })
    }

    fun `test Windows の PATH に katachi に渡す shims の場所が入る`() {
        assertEquals("C:\\tools;/Users/me\\scoop\\shims", locator("C:\\tools", windows = true).searchPath())
    }

    fun `test Windows では セミコロン区切りで bat と cmd と exe を探す`() {
        val candidates = locator("C:\\katachi\\bin;C:\\tools", windows = true).candidates().map { it.fileName.toString() }
        // Two PATH entries and the shims of Scoop.
        assertEquals(List(3) { listOf("katachi.bat", "katachi.cmd", "katachi.exe") }.flatten(), candidates)
    }
}
