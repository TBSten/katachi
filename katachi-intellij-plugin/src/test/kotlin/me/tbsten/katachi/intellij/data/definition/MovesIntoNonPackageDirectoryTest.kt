package me.tbsten.katachi.intellij.data.definition

import org.junit.Assert.assertEquals
import org.junit.Test

/** Mirrors the cases of katachi's `movesIntoNonPackageDirectory`: the plugin names no place that is no Kotlin package. */
class MovesIntoNonPackageDirectoryTest {
    private val hyphenated = "feature/user-profile/src/main/kotlin/com/example/userprofile/UserProfileScreen.kt"

    @Test
    fun `package のディレクトリをハイフンの値に直す移動は勧めない`() {
        val expected = "feature/user-profile/src/main/kotlin/com/example/user-profile/UserProfileScreen.kt"
        assertEquals(true, movesIntoNonPackageDirectory(hyphenated, expected))
    }

    @Test
    fun `package になる値への移動と ソースの外の移動は勧める`() {
        assertEquals(false, movesIntoNonPackageDirectory(hyphenated, "feature/user-profile/src/main/kotlin/com/example/profile/UserProfileScreen.kt"))
        assertEquals(false, movesIntoNonPackageDirectory("feature/a/docs/b-c/x.md", "feature/a/docs/a-b/x.md"))
    }
}
