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
            listOf("/a/katachi", "/b/katachi", "/opt/homebrew/bin/katachi", "/usr/local/bin/katachi", "/Users/me/.local/bin/katachi").map(::Path),
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

    fun `test Windows では セミコロン区切りで bat と cmd と exe を探す`() {
        val candidates = locator("C:\\katachi\\bin;C:\\tools", windows = true).candidates().map { it.fileName.toString() }
        assertEquals(listOf("katachi.bat", "katachi.cmd", "katachi.exe", "katachi.bat", "katachi.cmd", "katachi.exe"), candidates)
    }
}
