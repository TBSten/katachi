package me.tbsten.katachi.intellij.data.placement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The `<name.transform>` placeholder of a template's file pattern (design capture-check 3.9), next to `${name}` and `<name>`. */
class PathPatternTest {
    private fun parse(text: String): PathPattern = PathPattern.parse(text) ?: throw AssertionError("`$text` is left out of the index")

    @Test
    fun `名前つきの変換は元の値が決まっていれば その変換の1つだけに一致する`() {
        val pattern = parse("feature/\${feature}/<feature.pascalCase>Screen.kt")
        assertEquals(mapOf("feature" to "my-page"), pattern.matchFile(listOf("feature", "my-page", "MyPageScreen.kt"))?.decided)
        assertEquals(mapOf("feature" to "home"), pattern.matchFile(listOf("feature", "home", "HomeScreen.kt"))?.decided)
        assertNull(pattern.matchFile(listOf("feature", "my-page", "MypageScreen.kt")))
        assertNull(pattern.matchFile(listOf("feature", "my-page", "my-pageScreen.kt")))
        assertNull(pattern.matchFile(listOf("feature", "home", "ProfileScreen.kt")))
    }

    @Test
    fun `変換の無い派生は今までどおり変換の候補すべてに一致する`() {
        val pattern = parse("feature/\${feature}/<feature>Screen.kt")
        for (spelling in listOf("my-page", "MyPage", "myPage", "my_page", "MY_PAGE", "mypage")) {
            assertEquals(spelling, mapOf("feature" to "my-page"), pattern.matchFile(listOf("feature", "my-page", "${spelling}Screen.kt"))?.decided)
        }
    }

    @Test
    fun `元の値が未定の派生は1文字以上に一致し 残りの階層に派生が残る`() {
        val pattern = parse("<x.pascalCase>/\${x}/Foo.kt")
        val match = pattern.matchDirectory(listOf("Anything")) ?: throw AssertionError("no match")
        assertEquals(emptyMap<String, String>(), match.decided)
        assertEquals(listOf("x"), match.undecided)
        assertEquals(mapOf("x" to "any-thing"), pattern.matchFile(listOf("AnyThing", "any-thing", "Foo.kt"))?.decided)
    }

    @Test
    fun `知らない変換名のパターンは索引に入らない`() {
        assertNull(PathPattern.parse("feature/\${feature}/<feature.hologram>Screen.kt"))
    }

    @Test
    fun `変換は katachi の nameWords と同じ語で導く`() {
        val conversions = listOf("camelCase", "pascalCase", "kebabCase", "snakeCase", "screamingSnakeCase", "flatCase").map { it to (conversionOf(it) ?: throw AssertionError(it)) }
        assertEquals(listOf("myPage", "MyPage", "my-page", "my_page", "MY_PAGE", "mypage"), conversions.map { (_, convert) -> convert("my-page") })
        assertEquals(listOf("httpClient", "HttpClient", "http-client", "http_client", "HTTP_CLIENT", "httpclient"), conversions.map { (_, convert) -> convert("HTTPClient") })
        assertEquals(listOf("v2Api", "V2Api", "v2-api", "v2_api", "V2_API", "v2api"), conversions.map { (_, convert) -> convert("v2Api") })
        assertNull(conversionOf("hologram"))
    }
}
