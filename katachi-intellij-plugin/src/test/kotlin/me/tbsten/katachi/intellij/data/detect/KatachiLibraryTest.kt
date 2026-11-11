package me.tbsten.katachi.intellij.data.detect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Which library of the sync data is katachi, and its version (KATACHI-363). */
class KatachiLibraryTest {
    private fun named(vararg names: String) = SyncedLibrary(groupId = null, artifactId = null, version = null, names = names.toList())

    @Test
    fun `座標があればその版を返す`() {
        val library = SyncedLibrary("me.tbsten.katachi", "katachi", "0.4.0", listOf("Gradle: me.tbsten.katachi:katachi:0.4.0"))
        assertEquals("0.4.0", katachiVersionOf(library))
    }

    @Test
    fun `座標が空ならAndroid Studioのライブラリ名から版を読む`() {
        assertEquals("0.4.0", katachiVersionOf(named("Gradle: me.tbsten.katachi:katachi:0.4.0")))
        assertEquals("0.4.0", katachiVersionOf(named("me.tbsten.katachi:katachi:0.4.0")))
    }

    @Test
    fun `座標が合っていて版だけ無ければ名前の版を使う`() {
        val library = SyncedLibrary("me.tbsten.katachi", "katachi", null, listOf("Gradle: me.tbsten.katachi:katachi:0.4.0"))
        assertEquals("0.4.0", katachiVersionOf(library))
    }

    @Test
    fun `名前の末尾の種類の印は版に含めない`() {
        assertEquals("0.4.0", katachiVersionOf(named("Gradle: me.tbsten.katachi:katachi:0.4.0@jar")))
    }

    @Test
    fun `同じgroupのほかのartifactはkatachiとみなさない`() {
        assertNull(katachiVersionOf(named("Gradle: me.tbsten.katachi:katachi-konsist:0.4.0")))
        assertNull(katachiVersionOf(SyncedLibrary("me.tbsten.katachi", "katachi-konsist", "0.4.0", emptyList())))
    }

    @Test
    fun `座標の形でない名前はkatachiとみなさない`() {
        assertNull(katachiVersionOf(named("Gradle: me.tbsten.katachi:katachi", "katachi", "")))
    }
}
