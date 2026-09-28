package me.tbsten.katachi.intellij.data.placement

import me.tbsten.katachi.intellij.data.gradle.GradleTaskInvocation
import org.junit.Assert.assertEquals
import org.junit.Test

class GenerateCommandTest {
    // covers: 論点16
    @Test
    fun `安全な語はそのまま並べる`() {
        val command = generateCommandOf(
            GradleTaskInvocation(":arch-a:katachiTemplate", listOf("template" to "data.Repository", "onExisting" to "overwrite", "name" to "User")),
        )
        assertEquals(
            "./gradlew :arch-a:katachiTemplate --arg template=data.Repository --arg onExisting=overwrite --arg name=User",
            command,
        )
    }

    // covers: 論点16
    @Test
    fun `空白と引用符とドル記号とバッククォートと日本語を含む値は単一引用符で包む`() {
        val command = generateCommandOf(
            GradleTaskInvocation(":a:katachiTemplate", listOf("v" to "it's \$HOME `x` 日本語 \"q\"", "e" to "")),
        )
        assertEquals(
            "./gradlew :a:katachiTemplate --arg 'v=it'\\''s \$HOME `x` 日本語 \"q\"' --arg e=",
            command,
        )
    }

    // covers: 論点16
    @Test
    fun `引数が無ければタスクだけ`() {
        assertEquals("./gradlew :a:katachiTemplate", generateCommandOf(GradleTaskInvocation(":a:katachiTemplate")))
    }
}
