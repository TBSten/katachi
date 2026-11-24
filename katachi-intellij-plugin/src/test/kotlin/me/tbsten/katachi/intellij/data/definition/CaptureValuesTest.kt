package me.tbsten.katachi.intellij.data.definition

import me.tbsten.katachi.intellij.model.CaptureRef
import me.tbsten.katachi.intellij.model.LayoutEntryKind
import me.tbsten.katachi.intellij.testing.layoutEntry
import org.junit.Assert.assertEquals
import org.junit.Test

/** What a `capture(...)` of a layout entry took from the path of one of its files. */
class CaptureValuesTest {
    @Test
    fun `a capture on a star of the file name gives what the star matched`() {
        val entry = layoutEntry("src/*Service.kt", captures = listOf(CaptureRef("name", 1)))

        assertEquals(listOf(CapturedValue("name", "User")), CaptureValues.of(entry, "src/UserService.kt"))
    }

    @Test
    fun `two captures on one level are told apart by their star`() {
        val entry = layoutEntry("src/*/*Service.kt", captures = listOf(CaptureRef("feature", 1), CaptureRef("name", 2)))

        assertEquals(
            listOf(CapturedValue("feature", "user"), CapturedValue("name", "Profile")),
            CaptureValues.of(entry, "src/user/ProfileService.kt"),
        )
    }

    @Test
    fun `a derived capture and a path the pattern does not match give nothing`() {
        val derived = layoutEntry("src/*Service.kt", captures = listOf(CaptureRef("name", 1, transform = "pascalCase")))

        assertEquals(emptyList<CapturedValue>(), CaptureValues.of(derived, "src/UserService.kt"))
        assertEquals(emptyList<CapturedValue>(), CaptureValues.of(layoutEntry("src/*Service.kt", captures = listOf(CaptureRef("name", 1))), "lib/UserService.kt"))
    }

    @Test
    fun `a directory entry gives nothing`() {
        val entry = layoutEntry("src/*", LayoutEntryKind.Directory, captures = listOf(CaptureRef("name", 1)))

        assertEquals(emptyList<CapturedValue>(), CaptureValues.of(entry, "src/User"))
    }
}
