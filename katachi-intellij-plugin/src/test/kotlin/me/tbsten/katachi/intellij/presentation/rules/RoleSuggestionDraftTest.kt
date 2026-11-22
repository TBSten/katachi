package me.tbsten.katachi.intellij.presentation.rules

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The draft is the text katachi's report prints under an UnexpectedFile / UnexpectedDirectory (`RoleSuggestion.kt`; its
 * cases are in `ViolationReportSpec`), so the notification and the test report give one answer for a file. The expected
 * texts below are copied from that spec.
 */
class RoleSuggestionDraftTest {
    @Test
    fun `ディレクトリの下の kt ファイルは ktFile の記法でディレクトリと割り算でつなぐ`() {
        assertEquals(
            """
            "TokenRefresher".role {
              layout {
                "core/domain" / "TokenRefresher".ktFile()
              }
            }
            """.trimIndent(),
            roleSuggestionDraftFor("core/domain/TokenRefresher.kt"),
        )
    }

    @Test
    fun `kts と kt 以外は拡張子ごとの記法になり名前は英数字の区切りで大文字にそろえる`() {
        assertEquals(
            """
            "BuildGradle".role {
              layout {
                "build.gradle".ktsFile()
              }
            }
            """.trimIndent(),
            roleSuggestionDraftFor("build.gradle.kts"),
        )
        assertEquals(
            """
            "LibsVersionsToml".role {
              layout {
                "libs.versions.toml".file()
              }
            }
            """.trimIndent(),
            roleSuggestionDraftFor("libs.versions.toml"),
        )
    }

    @Test
    fun `名前に使える文字が無いか数字で始まるなら名前は TODO になる`() {
        assertEquals("TODO", suggestedRoleNameFor("core/123.kt"))
        assertEquals("TODO", suggestedRoleNameFor("core/---.kt"))
        assertEquals("Paging", suggestedRoleNameFor("core/domain/paging.kt"))
        assertEquals("TokenRefresher", suggestedRoleNameFor("TokenRefresher.kt"))
    }

    @Test
    fun `ルート直下のファイルはディレクトリ無しで書く`() {
        assertEquals("\"Foo\".role {\n  layout {\n    \"Foo\".ktFile()\n  }\n}", roleSuggestionDraftFor("Foo.kt"))
    }

    @Test
    fun `OS が自動で作るファイルには Role の案ではなく gitignore に足す名前を出す`() {
        // A role for it passes where Finder writes it and fails with MissingFile on CI, as katachi's report says.
        assertEquals(".DS_Store", roleSuggestionDraftFor("feature/home/.DS_Store"))
        assertEquals("Thumbs.db", roleSuggestionDraftFor("Thumbs.db"))
        assertEquals("Desktop.ini", roleSuggestionDraftFor("docs/Desktop.ini"))
        assertEquals(true, isOsWrittenFile("a/b/.DS_Store"))
        assertEquals(false, isOsWrittenFile("a/b/DS_Store.kt"))
    }
}
