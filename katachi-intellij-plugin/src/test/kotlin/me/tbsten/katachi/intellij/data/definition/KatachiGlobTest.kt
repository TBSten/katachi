package me.tbsten.katachi.intellij.data.definition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The port of katachi's `Glob`: its grammar, `literalPrefix` and equality. The answers shared with katachi are in [KatachiGlobConformanceTest]. */
class KatachiGlobTest {
    private fun problemOf(pattern: String, separator: Char = '/'): KatachiGlobProblem =
        runCatching { KatachiGlob.compile(pattern, separator) }.exceptionOrNull()
            .let { it as? KatachiGlobSyntaxException }?.problem
            ?: throw AssertionError("`$pattern` compiled")

    @Test
    fun `単独のアスタリスクは1階層だけで区切りをまたがない`() {
        val glob = KatachiGlob.compile("src/*/Foo.kt")
        assertTrue(glob.matches("src/main/Foo.kt"))
        assertFalse(glob.matches("src/main/kotlin/Foo.kt"))
        assertFalse(glob.matches("src//Foo.kt"))
    }

    @Test
    fun `二重アスタリスクは0階層以上で前後の区切りごと省ける`() {
        val glob = KatachiGlob.compile("a/**/b")
        assertTrue(glob.matches("a/b"))
        assertTrue(glob.matches("a/x/y/b"))
        assertFalse(glob.matches("a/x/c"))
    }

    @Test
    fun `モジュールパスの区切りでは先頭の区切りは根を表しコロンをまたがない`() {
        val glob = KatachiGlob.compile(":feature:*", KatachiGlob.MODULE_SEPARATOR)
        assertTrue(glob.matches(":feature:home"))
        assertFalse(glob.matches(":feature:home:impl"))
        assertFalse(glob.matches(":feature"))
        assertEquals(emptyList<String>(), glob.literalPrefix)
    }

    @Test
    fun `ワイルドカードの手前までのリテラルの階層が literalPrefix になりエスケープは外される`() {
        assertEquals(listOf("core", "domain"), KatachiGlob.compile("core/domain/*UseCase.kt").literalPrefix)
        assertEquals(listOf("a*b"), KatachiGlob.compile("""a\*b/c*""").literalPrefix)
        assertEquals(emptyList<String>(), KatachiGlob.compile("**/x").literalPrefix)
        assertEquals(listOf("build.gradle.kts"), KatachiGlob.compile("build.gradle.kts").literalPrefix)
    }

    @Test
    fun `hasWildcard はアスタリスクがあるときだけ真でエスケープしたものは数えない`() {
        assertFalse(KatachiGlob.compile("build.gradle.kts").hasWildcard)
        assertFalse(KatachiGlob.compile("""a\*b""").hasWildcard)
        assertTrue(KatachiGlob.compile("*.kt").hasWildcard)
        assertTrue(KatachiGlob.compile("a/**").hasWildcard)
    }

    @Test
    fun `他のツールの glob の記号は拒否し理由の種類を返す`() {
        assertEquals(KatachiGlobProblem.RejectedMetacharacter('?'), problemOf("Foo?.kt"))
        assertEquals(KatachiGlobProblem.RejectedMetacharacter('{'), problemOf("{a,b}.kt"))
        assertEquals(KatachiGlobProblem.RejectedMetacharacter('['), problemOf("[abc].kt"))
        assertEquals(KatachiGlobProblem.DoubleStarInsideSegment("a**b"), problemOf("a**b"))
        assertEquals(KatachiGlobProblem.TrailingBackslash, problemOf("""a\"""))
        assertEquals(KatachiGlobProblem.UnescapableCharacter('a'), problemOf("""\a.kt"""))
        assertEquals(KatachiGlobProblem.EmptyPattern, problemOf(""))
        assertEquals(KatachiGlobProblem.EmptySegment('/'), problemOf("a//b"))
        assertEquals(KatachiGlobProblem.EmptySegment(':'), problemOf(":feature::x", ':'))
    }

    @Test
    fun `例外の文面にパターンと直し方が入る`() {
        val message = runCatching { KatachiGlob.compile("Foo?.kt") }.exceptionOrNull()?.message.orEmpty()
        assertTrue(message, "Foo?.kt" in message)
        assertTrue(message, "released together" in message)
    }

    @Test
    fun `同じになるのはパターンと区切りが同じときだけ`() {
        assertEquals(KatachiGlob.compile("a/*"), KatachiGlob.compile("a/*"))
        assertEquals(KatachiGlob.compile("a/*").hashCode(), KatachiGlob.compile("a/*").hashCode())
        assertNotEquals(KatachiGlob.compile("a/*"), KatachiGlob.compile("a/**"))
        assertNotEquals(KatachiGlob.compile("a", '/'), KatachiGlob.compile("a", ':'))
    }

    @Test
    fun `大文字小文字は区別する`() {
        assertFalse(KatachiGlob.compile("Foo.kt").matches("foo.kt"))
    }

    @Test
    fun `matchGroups はワイルドカードごとに取った文字を返し 二重アスタリスクは取らなかったとき空になる`() {
        val glob = KatachiGlob.compile("feature/*/src/**/feature/*/*Screen.kt")
        assertEquals(listOf("home", "main/kotlin", "home", "Home"), glob.matchGroups("feature/home/src/main/kotlin/feature/home/HomeScreen.kt"))
        assertEquals(listOf("home", "", "home", "Home"), glob.matchGroups("feature/home/src/feature/home/HomeScreen.kt"))
        assertEquals(null, glob.matchGroups("feature/home/Other.kt"))
        assertEquals(listOf(GlobGroupKind.Single, GlobGroupKind.Recursive, GlobGroupKind.Single, GlobGroupKind.Single), glob.groupKinds)
        assertEquals(listOf(1, 3, 5, 6), glob.groupSegments)
        assertEquals(3, glob.singleWildcardCount)
    }

    @Test
    fun `同じ階層の2つのアスタリスクは左から順に群になり 階層と何番目かで選べる`() {
        val glob = KatachiGlob.compile("src/*/*-*.kt")
        val groups = glob.matchGroups("src/home/x-home.kt") ?: throw AssertionError("no match")
        assertEquals(listOf("home", "x", "home"), groups)
        assertEquals("home", glob.singleGroupValue(groups, segmentIndex = 1, wildcardIndex = 0))
        assertEquals("x", glob.singleGroupValue(groups, segmentIndex = 2, wildcardIndex = 0))
        assertEquals("home", glob.singleGroupValue(groups, segmentIndex = 2, wildcardIndex = 1))
        assertEquals(null, glob.singleGroupValue(groups, segmentIndex = 2, wildcardIndex = 2))
        assertEquals(null, glob.singleGroupValue(groups, segmentIndex = 0, wildcardIndex = 0))
        // The module separator counts levels after the leading `:`.
        assertEquals(listOf(1), KatachiGlob.compile(":feature:*", KatachiGlob.MODULE_SEPARATOR).groupSegments)
        assertEquals(listOf("home"), KatachiGlob.compile(":feature:*", KatachiGlob.MODULE_SEPARATOR).matchGroups(":feature:home"))
    }
}
