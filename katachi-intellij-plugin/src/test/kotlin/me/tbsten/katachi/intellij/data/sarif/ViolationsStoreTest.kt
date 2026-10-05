package me.tbsten.katachi.intellij.data.sarif

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.tbsten.katachi.intellij.data.NioProjectFileSystem
import me.tbsten.katachi.intellij.data.gradle.GradleRunListener
import me.tbsten.katachi.intellij.data.gradle.GradleRunOutcome
import me.tbsten.katachi.intellij.data.gradle.GradleRunRequest
import me.tbsten.katachi.intellij.data.gradle.GradleTaskRunner
import me.tbsten.katachi.intellij.data.watch.FileChanges
import me.tbsten.katachi.intellij.model.KatachiModule
import me.tbsten.katachi.intellij.model.ViolationsState
import me.tbsten.katachi.intellij.testing.SarifResult
import me.tbsten.katachi.intellij.testing.sarifText
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.Collections

/** Reading the SARIF files and running the check (design 4.7), on a real disk with a fake Gradle. */
class ViolationsStoreTest {
    private val root: Path = Files.createTempDirectory("katachi-violations").toRealPath()
    private val arch = KatachiModule(":arch", root.resolve("arch"), root, "project")
    private val reports = arch.directory.resolve("build/reports/katachi")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val missing = Collections.synchronizedList(mutableListOf<String>())

    /** What each `:arch:test` does: runs [block] against the reports directory, after deleting it as katachi's test task does. */
    private class Checks(val reports: Path) : GradleTaskRunner {
        val requests = Collections.synchronizedList(mutableListOf<GradleRunRequest>())
        var block: suspend () -> GradleRunOutcome = { GradleRunOutcome.Succeeded }

        /** The output lines the run prints before [block] decides. */
        var output: List<String> = emptyList()

        override suspend fun run(request: GradleRunRequest, listener: GradleRunListener): GradleRunOutcome {
            requests += request
            reports.toFile().deleteRecursively()
            output.forEach(listener::onLine)
            return block()
        }
    }

    private val checks = Checks(reports)

    private val errors = Collections.synchronizedList(mutableListOf<String>())
    private val order = Collections.synchronizedList(mutableListOf<String>())

    private fun store(
        retryDelayMillis: Long = 50,
        listSarif: (Path) -> List<Path> = ::sarifFilesOf,
        beforeRead: (KatachiModule) -> Unit = {},
    ) = ViolationsStore(
        scope = scope,
        runner = checks,
        fileSystem = NioProjectFileSystem,
        modules = { listOf(arch) },
        listSarif = listSarif,
        retryDelayMillis = retryDelayMillis,
        onSarifMissing = { missing += it.gradlePath },
        beforeRead = beforeRead,
        onError = { message, cause -> errors += "$message: ${cause::class.simpleName}" },
    )

    private fun write(relative: String, text: String): Path = reports.resolve(relative).also {
        Files.createDirectories(it.parent)
        Files.writeString(it, text)
    }

    private fun sarif(vararg uris: String) = sarifText(root, uris.map { SarifResult("UnexpectedFile", it) })

    @After
    fun tearDown() {
        scope.cancel()
        root.toFile().deleteRecursively()
    }

    @Test
    fun `SARIF が無ければ NotRun でタスク別と2回目の SARIF もまとめて読む`() = runBlocking {
        val store = store()
        store.read()
        assertEquals(ViolationsState.NotRun, store.state.value)

        write("katachi.sarif", sarif("app/A.kt"))
        write("test/katachi.sarif", sarif("app/B.kt"))
        write("test/katachi-2.sarif", sarif("app/C.kt"))
        write("test/other.json", "{}")
        store.read()

        val ready = store.state.value as ViolationsState.Ready
        assertEquals(listOf("A.kt", "B.kt", "C.kt"), ready.runs.map { it.location?.path?.fileName.toString() }.sorted())
        assertEquals(3, ready.paths.size)
        assertEquals(1, store.forPath(root.resolve("app/B.kt")).size)
        assertEquals(3, store.forModule(arch.id).size)
    }

    @Test
    fun `書きかけの SARIF は読み直して読めれば Ready`() = runBlocking {
        val store = store(retryDelayMillis = 200)
        val file = write("katachi.sarif", sarif("app/A.kt").take(40))
        scope.launch {
            kotlinx.coroutines.delay(150)
            Files.writeString(file, sarif("app/A.kt"))
        }
        store.read()
        assertEquals(1, (store.state.value as ViolationsState.Ready).runs.size)
    }

    @Test
    fun `読み直しても壊れていれば前の答えを持った Unreadable`() = runBlocking {
        val store = store()
        write("katachi.sarif", sarif("app/A.kt"))
        store.read()
        val previous = store.state.value as ViolationsState.Ready
        write("katachi.sarif", "{ \"version\": ")
        store.read()
        val unreadable = store.state.value as ViolationsState.Unreadable
        assertEquals(previous, unreadable.previous)
        assertEquals(1, store.forPath(root.resolve("app/A.kt")).size)
    }

    @Test
    fun `プロジェクトの変更で古くなり新しい SARIF を読むと新しくなる`() = runBlocking {
        val store = store()
        write("katachi.sarif", sarif("app/A.kt"))
        store.read()
        store.onFilesChanged(FileChanges(projectChanged = true))
        assertTrue((store.state.value as ViolationsState.Ready).stale)
        // The same files read again: still stale.
        store.read()
        assertTrue((store.state.value as ViolationsState.Ready).stale)
        Files.setLastModifiedTime(write("katachi.sarif", sarif("app/A.kt", "app/B.kt")), java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis() + 5_000))
        store.read()
        val ready = store.state.value as ViolationsState.Ready
        assertFalse(ready.stale)
        assertEquals(2, ready.runs.size)
    }

    @Test
    fun `検査の実行は走っている間 runningChecks に入り2回呼んでも Gradle は1回`() = runBlocking {
        val store = store()
        val gate = CompletableDeferred<Unit>()
        val started = CompletableDeferred<Unit>()
        checks.block = {
            started.complete(Unit)
            gate.await()
            write("katachi.sarif", sarif("app/A.kt"))
            GradleRunOutcome.Failed // violations fail the tests
        }
        val first = store.runCheck(arch.id)
        val second = store.runCheck(arch.id)
        started.await()
        assertTrue(first === second)
        assertEquals(setOf(arch.id), store.runningChecks.value)
        gate.complete(Unit)
        assertEquals(GradleRunOutcome.Failed, first.await())
        assertEquals(emptySet<Any>(), store.runningChecks.value)
        assertEquals(1, checks.requests.size)
        assertEquals(listOf(":arch:test"), checks.requests.single().taskNames)
        assertEquals(1, (store.state.value as ViolationsState.Ready).runs.size)
        assertEquals(emptyList<String>(), missing)
    }

    @Test
    fun `検査中に前の報告が消えても NotRun にせず前の違反を見せ続ける`() = runBlocking {
        val store = store()
        write("katachi.sarif", sarif("app/A.kt"))
        store.read()
        val gate = CompletableDeferred<Unit>()
        val started = CompletableDeferred<Unit>()
        checks.block = {
            started.complete(Unit)
            gate.await()
            write("katachi.sarif", sarif("app/B.kt"))
            GradleRunOutcome.Succeeded
        }
        val run = store.runCheck(arch.id)
        started.await()
        // The deletion's VFS event arrives while the check runs.
        store.onFilesChanged(FileChanges(sarifChanged = setOf(arch.id)))
        store.refresh().join()
        val during = store.state.value as ViolationsState.Ready
        assertEquals(listOf("A.kt"), during.runs.map { it.location?.path?.fileName.toString() })
        gate.complete(Unit)
        run.await()
        assertEquals(listOf("B.kt"), (store.state.value as ViolationsState.Ready).runs.map { it.location?.path?.fileName.toString() })
    }

    @Test
    fun `成功したのに SARIF が無ければ report sarif の案内を出し NotRun のまま`() = runBlocking {
        val store = store()
        assertEquals(GradleRunOutcome.Succeeded, store.runCheck(arch.id).await())
        assertEquals(listOf(":arch"), missing)
        assertEquals(ViolationsState.NotRun, store.state.value)
    }

    @Test
    fun `知らないモジュールの検査は Gradle を走らせず失敗`() = runBlocking {
        val store = store()
        val other = arch.copy(gradlePath = ":other")
        assertEquals(GradleRunOutcome.Failed, withTimeout(5_000) { store.runCheck(other.id).await() })
        assertEquals(0, checks.requests.size)
        assertEquals(emptySet<Any>(), store.runningChecks.first())
    }

    @Test
    fun `テストが失敗したのに SARIF が無ければ report sarif の案内を出す`() = runBlocking {
        val store = store()
        checks.output = listOf("> Task :arch:test FAILED", "ServiceRoleTest > UnexpectedFile FAILED", "BUILD FAILED in 3s")
        checks.block = { GradleRunOutcome.Failed }
        assertEquals(GradleRunOutcome.Failed, store.runCheck(arch.id).await())
        assertEquals(listOf(":arch"), missing)
        assertEquals(ViolationsState.NotRun, store.state.value)
    }

    @Test
    fun `コンパイルが通らないとき・タスクが無いときは SARIF が無くても案内を出さない`() = runBlocking {
        val store = store()
        checks.output = listOf("e: file:///p/arch/src/test/kotlin/A.kt:3:1 Unresolved reference: Foo", "BUILD FAILED in 3s")
        checks.block = { GradleRunOutcome.Failed }
        store.runCheck(arch.id).await()
        checks.output = listOf("Task 'test' not found in project ':arch'.", "BUILD FAILED in 1s")
        store.runCheck(arch.id).await()
        assertEquals(emptyList<String>(), missing)
    }

    @Test
    fun `SARIF が書かれて失敗したときは案内を出さない`() = runBlocking {
        val store = store()
        checks.block = {
            write("katachi.sarif", sarif("app/A.kt"))
            GradleRunOutcome.Failed
        }
        store.runCheck(arch.id).await()
        assertEquals(emptyList<String>(), missing)
    }

    @Test
    fun `読むときの想定外の例外は Unreadable で前の答えを残しサービスの scope に漏らさない`() = runBlocking {
        var explode = false
        val store = store(listSarif = { dir -> if (explode) throw java.io.UncheckedIOException(java.io.IOException("deleted while listing")) else sarifFilesOf(dir) })
        write("katachi.sarif", sarif("app/A.kt"))
        store.read()
        val previous = store.state.value as ViolationsState.Ready
        explode = true
        store.refresh().join()
        val unreadable = store.state.value as ViolationsState.Unreadable
        assertEquals(previous, unreadable.previous)
        assertTrue(unreadable.reason, "UncheckedIOException" in unreadable.reason)
        assertEquals(1, errors.size)
        // The next read that works gives the answer back.
        explode = false
        store.refresh().join()
        assertTrue(store.state.value is ViolationsState.Ready)
    }

    @Test
    fun `検査を走らせる Gradle が例外を投げても Failed で終わり runningChecks から外れる`() = runBlocking {
        val store = store()
        checks.block = { throw IllegalStateException("the daemon died") }
        assertEquals(GradleRunOutcome.Failed, withTimeout(5_000) { store.runCheck(arch.id).await() })
        assertEquals(emptySet<Any>(), store.runningChecks.value)
        assertEquals(1, errors.size)
    }

    @Test
    fun `一覧の前に beforeRead がモジュールごとに呼ばれる`() = runBlocking {
        val store = store(listSarif = { order += "list"; emptyList() }, beforeRead = { order += "before ${it.gradlePath}" })
        store.read()
        assertEquals(listOf("before :arch", "list"), order.toList())
    }
}
