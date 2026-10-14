package me.tbsten.katachi.intellij.data.watch

import me.tbsten.katachi.intellij.testing.module
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Paths
import kotlin.random.Random

class FileChangesTest {
    private val root = Paths.get("/work/project")
    private val arch = module(":arch")
    private val watched = listOf(WatchedModule(arch, emptyList()))

    private fun classify(vararg paths: String, own: Set<String> = emptySet(), modules: List<WatchedModule> = watched) =
        classifyChanges(paths.map { root.resolve(it) }, modules, listOf(root)) { it in own.map(root::resolve) }

    @Test
    fun `定義モジュールのテストソースの変更は定義の変更で自動再読み込みの候補`() {
        val changes = classify("arch/src/test/kotlin/UseCaseRole.kt")
        assertEquals(setOf(arch.id), changes.definitionChanged)
        assertEquals(setOf(arch.id), changes.testSourcesChanged)
        assertTrue(changes.projectChanged)
    }

    @Test
    fun `build スクリプトと main ソースの変更は定義の変更だが自動再読み込みの候補ではない`() {
        val changes = classify("arch/build.gradle.kts", "arch/src/main/kotlin/Helper.kt")
        assertEquals(setOf(arch.id), changes.definitionChanged)
        assertEquals(emptySet<Any>(), changes.testSourcesChanged)
    }

    @Test
    fun `IDE の知るテストソースルートがあればそこだけを候補にする`() {
        val modules = listOf(WatchedModule(arch, listOf(arch.directory.resolve("src/archTest/kotlin"))))
        assertEquals(setOf(arch.id), classify("arch/src/archTest/kotlin/A.kt", modules = modules).testSourcesChanged)
        assertEquals(emptySet<Any>(), classify("arch/src/test/kotlin/A.kt", modules = modules).testSourcesChanged)
    }

    @Test
    fun `build の下の SARIF は違反の変更でプロジェクトの変更にも定義の変更にもならない`() {
        val changes = classify("arch/build/reports/katachi/katachi.sarif", "arch/build/reports/katachi/test/katachi-2.sarif")
        assertEquals(setOf(arch.id), changes.sarifChanged)
        assertEquals(emptySet<Any>(), changes.definitionChanged)
        assertFalse(changes.projectChanged)
    }

    @Test
    fun `報告のディレクトリごと作られたり消されたりしても違反の変更にする`() {
        assertEquals(setOf(arch.id), classify("arch/build/reports/katachi").sarifChanged)
        assertEquals(setOf(arch.id), classify("arch/build/reports").sarifChanged)
        assertEquals(setOf(arch.id), classify("arch/build/reports/katachi/test").sarifChanged)
        assertEquals(emptySet<Any>(), classify("arch/build/reports/tests").sarifChanged)
    }

    @Test
    fun `build と gradle と idea の下は何にもならない`() {
        val changes = classify("arch/build/classes/A.class", "app/build/tmp/x", ".gradle/8.0/file", ".idea/workspace.xml", "arch/build/reports/katachi/index.html")
        assertTrue(changes.isEmpty)
    }

    @Test
    fun `git の中の更新はコミットや fetch のたびに起きてもプロジェクトの変更にならない`() {
        val changes = classify(".git/index", ".git/HEAD", ".git/logs/HEAD", ".git/FETCH_HEAD", "arch/.git/index")
        assertTrue(changes.isEmpty)
    }

    @Test
    fun `ソースの中の build という名前のパッケージはプロジェクトの変更で build ディレクトリの出力ではない`() {
        val changes = classify("app/src/main/kotlin/com/example/build/Foo.kt")
        assertTrue(changes.projectChanged)
        val definition = classify("arch/src/test/kotlin/com/example/build/Rules.kt")
        assertEquals(setOf(arch.id), definition.definitionChanged)
        assertEquals(setOf(arch.id), definition.testSourcesChanged)
        // And a module's own build output, one or two levels down, still is output.
        assertTrue(classify("app/build/generated/ksp/main/kotlin/A.kt", "build/tmp/x", "libs/core/build/classes/A.class").isEmpty)
    }

    @Test
    fun `自分で書いているファイルは定義の変更にしないがプロジェクトの変更にはする`() {
        val changes = classify("arch/src/test/kotlin/Generated.kt", own = setOf("arch/src/test/kotlin/Generated.kt"))
        assertEquals(emptySet<Any>(), changes.definitionChanged)
        assertTrue(changes.projectChanged)
    }

    @Test
    fun `プロジェクトルートの外は何にもならない`() {
        assertTrue(classify("/work/other/src/A.kt").isEmpty)
    }

    @Test
    fun `どんなパスの列でもテストソースの変更は定義の変更に含まれ SARIF は出力ディレクトリの下だけ`() {
        val segments = listOf("arch", "app", "src", "test", "main", "kotlin", "build", "reports", "katachi", ".gradle", ".idea", "A.kt", "katachi.sarif", "build.gradle.kts")
        val random = Random(20261005)
        repeat(500) {
            val paths = List(random.nextInt(1, 6)) { List(random.nextInt(1, 7)) { segments.random(random) }.joinToString("/") }
            val changes = classify(*paths.toTypedArray())
            assertTrue("$paths", changes.definitionChanged.containsAll(changes.testSourcesChanged))
            val reports = Paths.get("arch/build/reports/katachi")
            if (changes.sarifChanged.isNotEmpty()) {
                assertTrue("$paths", paths.map(Paths::get).any { (reports.startsWith(it) && it.startsWith("arch/build")) || it.startsWith(reports) })
            }
        }
    }

    @Test
    fun `読んだ baseline のファイルの変更だけが baselineChanged になる`() {
        val baseline = root.resolve("katachi-baseline.json")
        fun classifyWith(vararg paths: String) = classifyChanges(paths.map { root.resolve(it) }, watched, listOf(root), setOf(baseline)) { false }

        assertTrue(classifyWith("katachi-baseline.json").baselineChanged)
        assertFalse(classifyWith("docs/katachi-baseline.json", "src/Foo.kt").baselineChanged)
        assertFalse(classify("katachi-baseline.json").baselineChanged)
        assertFalse("a change that touches only a baseline is still not empty", classifyWith("katachi-baseline.json").isEmpty)
    }
}
