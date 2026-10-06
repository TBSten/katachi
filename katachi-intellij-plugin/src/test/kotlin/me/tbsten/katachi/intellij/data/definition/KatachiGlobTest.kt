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
    fun `単独のアスタリスクは1階層の中だけで区切りをまたがない`() {
        val glob = KatachiGlob.compile("src/*/Foo.kt")
        assertTrue(glob.matches("src/main/Foo.kt"))
        assertFalse(glob.matches("src/main/kotlin/Foo.kt"))
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
    fun `hasWildcard はアスタリスクとクエスチョンと文字クラスと選択肢のどれかがあるときだけ真でエスケープしたものは数えない`() {
        assertFalse(KatachiGlob.compile("build.gradle.kts").hasWildcard)
        assertFalse(KatachiGlob.compile("""a\*b""").hasWildcard)
        assertTrue(KatachiGlob.compile("*.kt").hasWildcard)
        assertTrue(KatachiGlob.compile("a/**").hasWildcard)
        for (pattern in listOf("Foo?.kt", "[ab].kt", "{a,b}.kt")) assertTrue(pattern, KatachiGlob.compile(pattern).hasWildcard)
        assertFalse(KatachiGlob.compile("""a\?\[\{""").hasWildcard)
    }

    @Test
    fun `アスタリスクは文字と混ざれば空にも当たり 段全体なら空の段を作らない`() {
        assertEquals(listOf(""), KatachiGlob.compile("app_icon*.xml").matchGroups("app_icon.xml"))
        assertFalse(KatachiGlob.compile("feature/*").matches("feature"))
        assertFalse(KatachiGlob.compile(":feature:*", ':').matches(":feature"))
    }

    @Test
    fun `クエスチョンと文字クラスと選択肢は値にならず区切り文字を越えない`() {
        assertTrue(KatachiGlob.compile("Foo?.kt").matches("FooA.kt"))
        assertFalse(KatachiGlob.compile("Foo?.kt").matches("Foo.kt"))
        assertFalse(KatachiGlob.compile("Foo?.kt").matches("Foo/.kt"))
        assertEquals(listOf("A"), KatachiGlob.compile("*?.kt").matchGroups("AB.kt"))
        assertTrue(KatachiGlob.compile("[a-c]x").matches("bx"))
        assertFalse(KatachiGlob.compile("[a-c]x").matches("Bx"))
        assertTrue(KatachiGlob.compile("[!a]x").matches("bx"))
        assertFalse(KatachiGlob.compile("a[!x]b").matches("a/b"))
        assertFalse(KatachiGlob.compile("a[.-9]b").matches("a/b"))
        assertTrue(KatachiGlob.compile("[-a].kt").matches("-.kt"))
        assertTrue(KatachiGlob.compile("*.{kt,kts}").matches("build.kts"))
        assertTrue(KatachiGlob.compile("build.gradle{,.kts}").matches("build.gradle"))
        assertEquals(listOf("HomeFoo"), KatachiGlob.compile("*{Screen,*Route}").matchGroups("HomeFooRoute"))
    }

    @Test
    fun `クエスチョンと文字クラスと選択肢を含む段で literalPrefix が止まる`() {
        assertEquals(listOf("src"), KatachiGlob.compile("src/{main,test}/kotlin").literalPrefix)
        assertEquals(listOf("src"), KatachiGlob.compile("src/ma?n/kotlin").literalPrefix)
        assertEquals(listOf("src"), KatachiGlob.compile("src/[mt]ain/kotlin").literalPrefix)
    }

    @Test
    fun `matchRanges は捕捉した各ワイルドカードの位置を返す`() {
        assertEquals(listOf(8..12), KatachiGlob.compile("app_icon*.xml").matchRanges("app_icon_dark.xml"))
        assertEquals(listOf(0..0, 5..5, 7..9), KatachiGlob.compile("*/?/{a,b}*/**").matchRanges("x/y/az/p/q"))
        assertEquals(listOf(null), KatachiGlob.compile("a/**/b").matchRanges("a/b"))
    }

    @Test
    fun `読めない glob の記号は拒否し理由の種類を返す`() {
        assertEquals(KatachiGlobProblem.UnclosedBracket("a[bc"), problemOf("a[bc"))
        assertEquals(KatachiGlobProblem.UnclosedBrace("a{b,c"), problemOf("x/a{b,c"))
        assertEquals(KatachiGlobProblem.UnmatchedClosing(']'), problemOf("a]"))
        assertEquals(KatachiGlobProblem.UnmatchedClosing('}'), problemOf("a}"))
        assertEquals(KatachiGlobProblem.EmptyCharacterClass, problemOf("[!]"))
        assertEquals(KatachiGlobProblem.ReversedRange('z', 'a'), problemOf("[z-a]"))
        assertEquals(KatachiGlobProblem.SeparatorInBrackets('/'), problemOf("[a/b]"))
        assertEquals(KatachiGlobProblem.EmptyBraces, problemOf("{}"))
        assertEquals(KatachiGlobProblem.NestedBraces, problemOf("{a,{b}}"))
        assertEquals(KatachiGlobProblem.DoubleStarInBraces, problemOf("{**}"))
        assertEquals(KatachiGlobProblem.SeparatorInBraces('/'), problemOf("{a/b,c}"))
        assertEquals(KatachiGlobProblem.SeparatorInBraces(':'), problemOf(":x:{a:b}", ':'))
        assertEquals(KatachiGlobProblem.DoubleStarInsideSegment("a**b"), problemOf("a**b"))
        assertEquals(KatachiGlobProblem.TrailingBackslash, problemOf("""a\"""))
        assertEquals(KatachiGlobProblem.UnescapableCharacter('a'), problemOf("""\a.kt"""))
        assertEquals(KatachiGlobProblem.EmptyPattern, problemOf(""))
        assertEquals(KatachiGlobProblem.EmptySegment('/'), problemOf("a//b"))
        assertEquals(KatachiGlobProblem.EmptySegment(':'), problemOf(":feature::x", ':'))
    }

    @Test
    fun `例外の文面にパターンと直し方が入る`() {
        val message = runCatching { KatachiGlob.compile("Foo[.kt") }.exceptionOrNull()?.message.orEmpty()
        assertTrue(message, "Foo[.kt" in message)
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
