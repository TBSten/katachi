package me.tbsten.katachi.intellij.data.definition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The bucketing only skips patterns; what matches, and in which order, is what a plain scan over every pattern gives. */
class PrefixGlobIndexTest {
    private val patterns = listOf(
        "core/domain/*UseCase.kt",
        "core/**",
        "**/README.md",
        "core/domain/useCase/*.kt",
        "build.gradle.kts",
        "*.kts",
        """a\*b/*.kt""",
        "core/*/src/**/Foo.kt",
        "feature/*/ui",
        ":feature:*",
    )
    private val index = PrefixGlobIndex(patterns) { KatachiGlob.compile(it, if (it.startsWith(":")) ':' else '/') }

    private fun scan(path: String): List<String> =
        patterns.filter { KatachiGlob.compile(it, if (it.startsWith(":")) ':' else '/').matches(path) }

    @Test
    fun `どの階層のバケツにあるパターンも拾い宣言順に返す`() {
        for (path in listOf("core/domain/GetUserUseCase.kt", "core/x/src/a/Foo.kt", "README.md", "a/b/README.md", "build.gradle.kts", "settings.gradle.kts", "a*b/c.kt", "feature/home/ui", "core")) {
            assertEquals(path, scan(path), index.matching(path))
        }
    }

    @Test
    fun `何にも当たらないパスは空`() {
        assertEquals(emptyList<String>(), index.matching("zzz/none.txt"))
        assertFalse(index.anyMatches("zzz/none.txt"))
    }

    @Test
    fun `anyMatches は matching が空でないときだけ真`() {
        assertTrue(index.anyMatches("core/domain/useCase/Foo.kt"))
        assertEquals(scan("core/domain/useCase/Foo.kt"), index.matching("core/domain/useCase/Foo.kt"))
    }

    @Test
    fun `別の区切りのパターンは全部のパスで試される`() {
        // `:feature:*` has a `:` separator, so it lives in the bucket every path reads and is still decided by a full match.
        assertEquals(listOf(":feature:*"), index.matching(":feature:home"))
    }
}
