package me.tbsten.katachi.intellij.data.gradle

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Paths

class GradleRunRequestTest {
    private val request = GradleRunRequest(
        Paths.get("/work/project"),
        listOf(GradleTaskInvocation(":arch:katachiTemplate", listOf("template" to "data.Repository", "name" to "My User's \"Repo\""))),
    )

    @Test
    fun `taskNamesはタスクパスとargとkey=valueを別々の要素に並べる`() {
        assertEquals(
            listOf(":arch:katachiTemplate", "--arg", "template=data.Repository", "--arg", "name=My User's \"Repo\""),
            request.taskNames,
        )
    }

    @Test
    fun `コピー用のコマンドは必要な語だけシングルクォートで囲む`() {
        assertEquals(
            """./gradlew :arch:katachiTemplate --arg template=data.Repository --arg 'name=My User'\''s "Repo"'""",
            request.commandLine,
        )
    }

    @Test
    fun `改行や日本語や空の値もクォートして1語にする`() {
        assertEquals("'a\nb'", shellQuoted("a\nb"))
        assertEquals("'名前=ユーザー'", shellQuoted("名前=ユーザー"))
        assertEquals("''", shellQuoted(""))
        assertEquals("name=User", shellQuoted("name=User"))
    }
}
