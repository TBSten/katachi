package me.tbsten.katachi.intellij.ide.dialog

import me.tbsten.katachi.intellij.model.KatachiModule
import me.tbsten.katachi.intellij.testing.module
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Path
import java.nio.file.Paths

class RootOnceOfTest {
    private val a = module(":arch-a")
    private val b = module(":arch-b")
    private val asked = mutableListOf<String>()
    private val resolve: (KatachiModule) -> Path = {
        asked += it.gradlePath
        Paths.get("/root", it.gradlePath.trim(':'))
    }

    @Test
    fun `開くときに定義ごとに1回だけ解き あとの問い合わせではディスクを見ない`() {
        val rootOf = rootOnceOf(listOf(a, b, a), resolve)
        assertEquals(listOf(":arch-a", ":arch-b"), asked)

        repeat(50) {
            rootOf(a)
            rootOf(b)
        }

        assertEquals(listOf(":arch-a", ":arch-b"), asked)
        assertEquals(Paths.get("/root/arch-a"), rootOf(a))
    }

    @Test
    fun `開いた後で増えた定義は最初の問い合わせで1回だけ解く`() {
        val rootOf = rootOnceOf(listOf(a), resolve)

        rootOf(b)
        rootOf(b)

        assertEquals(listOf(":arch-a", ":arch-b"), asked)
    }
}
