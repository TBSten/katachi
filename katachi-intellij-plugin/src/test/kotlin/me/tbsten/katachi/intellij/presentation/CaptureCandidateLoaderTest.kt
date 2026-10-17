package me.tbsten.katachi.intellij.presentation

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import me.tbsten.katachi.intellij.data.placement.DirectoryLister
import me.tbsten.katachi.intellij.data.placement.PathPattern
import me.tbsten.katachi.intellij.model.TemplateId
import me.tbsten.katachi.intellij.testing.ManualDispatcher
import me.tbsten.katachi.intellij.testing.module
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

/** [CaptureCandidateLoader] (design ide-review-screens 3.3): waits, reads in the background, drops what a newer request replaced. */
class CaptureCandidateLoaderTest {
    private val edt = ManualDispatcher()
    private val io = ManualDispatcher()
    private val scopeJob = Job()
    private val root: Path = Paths.get("/work")
    private val field = FieldId(TemplateId(module().id, "t"), "feature")
    private val pattern = PathPattern.parse("\${area}/\${feature}/src/\${name}.kt") ?: throw AssertionError()

    private var gate = CompletableDeferred<Unit>().also { it.complete(Unit) }
    private val reads = mutableListOf<Path>()
    private val loaded = mutableListOf<Pair<FieldId, CandidateList?>>()
    private val failures = mutableListOf<Throwable>()
    private var lister = DirectoryLister { directory ->
        reads.add(directory)
        when (directory.fileName.toString()) {
            "app" -> listOf("home", "settings")
            "lib" -> listOf("core")
            else -> emptyList()
        }
    }

    private fun loader() = CaptureCandidateLoader(
        scope = CoroutineScope(edt + scopeJob),
        lister = { directory -> lister.childDirectories(directory) },
        ioContext = io,
        settle = { gate.await() },
        onFailure = { failures += it },
        onLoaded = { id, list -> loaded += id to list },
    )

    private fun request(area: String? = "app", name: String? = null) = CandidateRequest(
        field, pattern, "feature", listOfNotNull(area?.let { "area" to it }, name?.let { "name" to it }).toMap(), root,
    )

    private fun drain() {
        while (edt.pending + io.pending > 0) {
            edt.runAll()
            io.runAll()
        }
    }

    @Test
    fun `300ms待つ間は読まず 待ち終えてからディスクを読んで届ける`() {
        gate = CompletableDeferred()
        val loader = loader()

        loader.request(request())
        drain()
        assertTrue(reads.isEmpty())
        assertTrue(loaded.isEmpty())

        gate.complete(Unit)
        drain()
        assertEquals(listOf(root.resolve("app")), reads)
        assertEquals(listOf(field to CandidateList(listOf("home", "settings"), 0, "app/{value}")), loaded)
    }

    @Test
    fun `読み終わる前に手前の階層が変わったら古い結果は捨てて新しい結果だけ届ける`() {
        val loader = loader()
        loader.request(request(area = "app"))
        edt.runAll() // 待ちを抜けて ディスクの読みが io に積まれた状態

        loader.request(request(area = "lib"))
        drain()

        assertEquals(listOf(field to CandidateList(listOf("core"), 0, "lib/{value}")), loaded)
    }

    @Test
    fun `読み終えた結果が届く前に新しい要求が来ても 古い方は届かない`() {
        val loader = loader()
        loader.request(request(area = "app"))
        edt.runAll()
        io.runAll() // 読み終わり 戻りが edt に積まれた

        loader.request(request(area = "lib"))
        drain()

        assertEquals(listOf(listOf("core")), loaded.map { it.second?.values })
    }

    @Test
    fun `鍵が同じ要求は読み直さず 強制か再読み込みなら読み直す`() {
        val loader = loader()
        loader.request(request(name = "A"))
        drain()

        loader.request(request(name = "B")) // 手前の階層には関わらない値
        drain()
        assertEquals(1, reads.size)

        loader.request(request(name = "B"), force = true)
        drain()
        assertEquals(2, reads.size)

        loader.reloadAll()
        drain()
        assertEquals(3, reads.size)
        assertEquals(3, loaded.size)
    }

    @Test
    fun `手前の階層が決まらないときは ディスクに触れずその場で候補なしと答える`() {
        val loader = loader()

        loader.request(request(area = null))

        assertEquals(listOf(field to null), loaded)
        assertEquals(0, edt.pending + io.pending)
        assertTrue(reads.isEmpty())
    }

    @Test
    fun `読めなかったら候補なしで 原因を知らせる`() {
        lister = DirectoryLister { throw IOException("denied") }
        val loader = loader()

        loader.request(request())
        drain()

        assertEquals(listOf(field to null), loaded)
        assertEquals(listOf("denied"), failures.map { it.message })
    }

    @Test
    fun `IOException以外の失敗も候補なしにして知らせる`() {
        lister = DirectoryLister { throw IllegalStateException("boom") }
        val loader = loader()

        loader.request(request())
        drain()

        assertEquals(listOf(field to null), loaded)
        assertEquals(listOf("boom"), failures.map { it.message })
    }

    @Test
    fun `取り消しは握りつぶさず 失敗にも候補なしにもしない`() {
        lister = DirectoryLister { throw CancellationException("stop") }
        val loader = loader()

        loader.request(request())
        drain()

        assertTrue(loaded.isEmpty())
        assertTrue(failures.isEmpty())
    }

    @Test
    fun `読んでいる最中にスコープが取り消されても何も届かない`() {
        val loader = loader()
        loader.request(request())
        edt.runAll()

        scopeJob.cancel()
        drain()

        assertTrue(loaded.isEmpty())
        assertTrue(failures.isEmpty())
    }

    @Test
    fun `忘れた欄の読みは届かず 再読み込みの対象からも外れる`() {
        val loader = loader()
        loader.request(request())
        edt.runAll()

        loader.forget(field)
        drain()
        loader.reloadAll()
        drain()

        assertTrue(loaded.isEmpty())
    }

    @Test
    fun `別スレッドで読み終えた結果は 先にロックを取った忘れる操作に負けて届かない`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val real = CaptureCandidateLoader(
            scope = CoroutineScope(Dispatchers.Default + scopeJob),
            lister = { directory ->
                reads.add(directory)
                entered.countDown()
                release.await(5, TimeUnit.SECONDS)
                listOf("old")
            },
            ioContext = Dispatchers.Default,
            settle = {},
            onLoaded = { id, list -> synchronized(loaded) { loaded += id to list } },
        )

        synchronized(real.lock) {
            real.request(request())
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            release.countDown()
            // The read ends on another thread and waits for the lock to deliver; the lock holder gets there first.
            Thread.sleep(150)
            real.forget(field)
        }
        Thread.sleep(150)

        synchronized(loaded) { assertEquals(emptyList<Pair<FieldId, CandidateList?>>(), loaded.toList()) }
    }
}
