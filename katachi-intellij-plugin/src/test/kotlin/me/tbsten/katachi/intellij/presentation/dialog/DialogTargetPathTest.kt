package me.tbsten.katachi.intellij.presentation.dialog

import me.tbsten.katachi.intellij.testing.capture
import me.tbsten.katachi.intellij.testing.file
import me.tbsten.katachi.intellij.testing.template
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.file.Paths

/** The dialog's sample path over a pattern deriving a level with a named conversion (design capture-check 3.9). */
class DialogTargetPathTest {
    private val root = Paths.get("/w/repo")
    private val pattern = "feature/\${feature}/<feature.pascalCase>Screen.kt"
    private val detail = template(
        "feature.Screen",
        parameters = emptyList(),
        files = listOf(file("<feature.pascalCase>Screen.kt", path = null, pattern = pattern, captures = listOf("feature"))),
        captures = listOf(capture("feature", pattern = "feature/*/*Screen.kt", position = 1)),
    ).detail ?: throw AssertionError("no detail")

    @Test
    fun `名前つきの変換は元の capture が入力されればプラグインが埋め katachi 待ちにしない`() {
        val target = dialogTargetOf(detail, mapOf("feature" to "my-page"), seeds = emptyMap(), root = root, origin = root)
        assertEquals("feature/my-page/MyPageScreen.kt", target.relativePath)
        assertEquals(root.resolve("feature/my-page/MyPageScreen.kt"), target.absolute)
        assertFalse(target.derivedUndecided)
    }

    @Test
    fun `元の capture が空のうちは派生もそのまま残り 絶対パスは決まらない`() {
        val target = dialogTargetOf(detail, emptyMap(), seeds = emptyMap(), root = root, origin = root)
        assertEquals(pattern, target.relativePath)
        assertNull(target.absolute)
        assertFalse(target.derivedUndecided)
    }

    @Test
    fun `変換の無い派生は今までどおり katachi に任せる`() {
        val moduleDetail = template(
            "feature.Screen",
            parameters = emptyList(),
            files = listOf(file("<feature>Screen.kt", path = null, pattern = "feature/\${feature}/<feature>Screen.kt", captures = listOf("feature"))),
            captures = listOf(capture("feature", pattern = ":feature:*", position = 0, module = true)),
        ).detail ?: throw AssertionError("no detail")
        val target = dialogTargetOf(moduleDetail, mapOf("feature" to "home"), seeds = emptyMap(), root = root, origin = root)
        assertEquals("feature/home/<feature>Screen.kt", target.relativePath)
        assertNull(target.absolute)
        assertEquals(true, target.derivedUndecided)
    }

    @Test
    fun `表示用のパスは未入力の capture を山かっこで見せ 派生の山かっこはそのまま残す`() {
        assertEquals("feature/<feature>/<name>Screen.kt", displayTargetPathOf("feature/\${feature}/\${name}Screen.kt"))
        assertEquals("feature/home/<feature.pascalCase>Screen.kt", displayTargetPathOf("feature/home/<feature.pascalCase>Screen.kt"))
        assertEquals("a/<x>/<y.camelCase>/<z>.kt", displayTargetPathOf("a/\${x}/<y.camelCase>/\${z}.kt"))
    }

    @Test
    fun `表示用のパスは入力済みの値や capture を含まないパスを変えない`() {
        assertEquals("feature/home/HomeScreen.kt", displayTargetPathOf("feature/home/HomeScreen.kt"))
        assertEquals("", displayTargetPathOf(""))
    }

    @Test
    fun `表示用に変えても生成先の相対パスそのものは dollar 波かっこのまま`() {
        val target = dialogTargetOf(detail, emptyMap(), seeds = emptyMap(), root = root, origin = root)
        assertEquals(pattern, target.relativePath)
        assertEquals("feature/<feature>/<feature.pascalCase>Screen.kt", displayTargetPathOf(target.relativePath))
    }
}
