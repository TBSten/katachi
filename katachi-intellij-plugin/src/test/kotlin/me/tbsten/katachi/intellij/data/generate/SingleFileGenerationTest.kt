package me.tbsten.katachi.intellij.data.generate

import kotlinx.coroutines.runBlocking
import me.tbsten.katachi.intellij.data.gradle.GradleRunOutcome
import me.tbsten.katachi.intellij.data.placement.EmptyFileRule
import me.tbsten.katachi.intellij.data.placement.TemplatePlacementIndex
import me.tbsten.katachi.intellij.model.DescriptionSnapshot
import me.tbsten.katachi.intellij.model.LoadFailure
import me.tbsten.katachi.intellij.model.ModuleTemplate
import me.tbsten.katachi.intellij.presentation.entry.EntryGenerationLedger
import me.tbsten.katachi.intellij.presentation.entry.LedgerEntry
import me.tbsten.katachi.intellij.testing.FakeFileSystem
import me.tbsten.katachi.intellij.testing.FakeGenerationCatalog
import me.tbsten.katachi.intellij.testing.FakeGenerationIdeEffects
import me.tbsten.katachi.intellij.testing.FakeGradleTaskRunner
import me.tbsten.katachi.intellij.testing.FakeRun
import me.tbsten.katachi.intellij.testing.ROOT
import me.tbsten.katachi.intellij.testing.editorFile
import me.tbsten.katachi.intellij.testing.module
import me.tbsten.katachi.intellij.testing.placementMatch
import me.tbsten.katachi.intellij.testing.rowOfTemplate
import me.tbsten.katachi.intellij.testing.rowsOf
import me.tbsten.katachi.intellij.testing.singleFileRequest
import me.tbsten.katachi.intellij.testing.underRoot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Path
import java.time.Instant

/**
 * The flow of a generation from an entry over fakes (plan chapter 1 "生成の流れ", decisions 1, 2, 11–14, 18):
 * the order of its steps, what it writes first, when it refuses and how it fails.
 */
class SingleFileGenerationTest {
    private val fs = FakeFileSystem()
    private val effects = FakeGenerationIdeEffects(fs)
    private val ledger = EntryGenerationLedger()
    private val rows = rowsOf("sample-jvm-with-captures")
    private val controller = rows.rowOfTemplate("api.Controller")
    private val target = underRoot("src/main/kotlin/com/example/controller/user/UserController.kt")
    private val generated = "package com.example.controller.user\n\nclass UserController\n"

    /** Reloads answer with [reloaded]; each reload and each build is logged into the effects' log, in order. */
    private var reloaded: (Int) -> GenerationCatalogReload = { reloadedWith(rows) }
    private val catalog = FakeGenerationCatalog { _, count -> effects.log += "json"; reloaded(count) }
    private var build: (Int) -> FakeRun = { success(target to generated) }
    private val runner = FakeGradleTaskRunner(fs) { _, count -> effects.log += "gradle"; build(count) }
    private val generation = SingleFileGeneration(effects, runner, fs, catalog, ledger)

    private fun request(to: Path = target, args: List<Pair<String, String>> = listOf("resource" to "user", "name" to "User")) =
        singleFileRequest(placementMatch(controller.template, module()), editorFile(ROOT.relativize(to).toString()), args, to)

    private fun reloadedWith(templates: List<ModuleTemplate>) = GenerationCatalogReload.Reloaded(
        listOf(DescriptionSnapshot(module(), "0.3.0", templates.map { it.template }, Instant.EPOCH)),
        TemplatePlacementIndex.EMPTY,
    )

    private fun success(vararg files: Pair<Path, String>) = FakeRun(
        lines = listOf("> Task :arch-a:katachiTemplate", "  [template] Generating ${files.size} files under ${ROOT.toUri()}") +
            files.map { "  [template] Wrote ${it.first.toUri()}" } + "BUILD SUCCESSFUL in 1s",
        writes = files.toMap(),
    )

    private val failure = FakeRun(listOf("> Task :arch-a:katachiTemplate FAILED", "", "BUILD FAILED in 1s"), GradleRunOutcome.Failed)

    private fun run(request: SingleFileGenerationRequest = request()) = runBlocking { generation.run(request) }

    // covers: 論点16
    @Test
    fun `仮のファイルを書いてから JSON を作り直しその後に生成する`() {
        val result = run()

        assertEquals(listOf("mkdirs", "provisional", "json", "gradle", "refresh", "reload"), effects.log)
        assertEquals(SingleFileGenerationResult.Generated(listOf(target)), result)
        assertEquals(generated, fs.readText(target))
        assertEquals(LedgerEntry.Succeeded(controller.id), ledger.entryOf(target))
        assertEquals(target, catalog.ownWrites.last())
        assertTrue(catalog.ownWrites.toString(), target.parent in catalog.ownWrites)
    }

    // covers: 論点2
    @Test
    fun `生成は選んだテンプレート1つと onExisting=overwrite で走らせる`() {
        run()

        val invocation = runner.requests.single().tasks.single()
        assertEquals(":arch-a:katachiTemplate", invocation.taskPath)
        assertEquals(listOf("template" to "api.Controller", "onExisting" to "overwrite", "resource" to "user", "name" to "User"), invocation.args)
    }

    // covers: 論点16
    @Test
    fun `仮の中身は拡張子のコメントで案内と実際のコマンドを書きパッケージ名はソースルートから`() {
        effects.packages = mapOf(target.parent to "com.example.controller.user")
        build = { failure }

        run()

        val text = effects.documents.getValue(target)
        assertTrue(text, text.startsWith("package com.example.controller.user\n\n// katachi: generating this file from the template コントローラ\n"))
        assertTrue(text, text.contains("// ./gradlew :arch-a:katachiTemplate --arg template=api.Controller --arg onExisting=overwrite --arg resource=user --arg name=User\n"))
        assertTrue(EmptyFileRule.isEmpty(text, "UserController.kt"))
    }

    // covers: 論点16
    @Test
    fun `コメントを書けない拡張子の仮の中身は空で、書ける拡張子はそのコメントだけ`() {
        val expected = mapOf("json" to null, "properties" to "# ", "xml" to "<!-- ", "java" to "// ", "txt" to null)
        for ((extension, marker) in expected) {
            val to = underRoot("src/main/kotlin/com/example/controller/user/User.$extension")
            reloaded = { GenerationCatalogReload.Failed(LoadFailure.Cancelled) }

            run(request(to))

            val text = fs.readText(to) ?: throw AssertionError("not written: $to")
            if (marker == null) {
                assertEquals(extension, "", text)
            } else {
                assertTrue("$extension: $text", text.lines().filter { it.isNotEmpty() }.all { it.startsWith(marker) })
                assertTrue(extension, EmptyFileRule.isEmpty(text, to.fileName.toString()))
            }
        }
    }

    // covers: 論点16
    @Test
    fun `ソースルートの外では package 行を書かない`() {
        build = { failure }

        run()

        assertFalse(effects.documents.getValue(target).contains("package"))
    }

    // covers: 論点6
    @Test
    fun `中身のあるファイルには何も書かずに断る`() {
        fs.write(target, "class UserController\n")

        val result = run()

        assertEquals(SingleFileGenerationResult.Refused(EntryGenerationRefusal.TargetHasContent(target)), result)
        assertEquals(emptyList<String>(), effects.log)
        assertEquals("class UserController\n", fs.readText(target))
    }

    // covers: 論点6
    @Test
    fun `空のファイルに未保存で class A を打った状態では未保存の中身を見て断る`() {
        fs.write(target, "")
        effects.typeUnsaved(target, "class A")

        val result = run()

        assertEquals(SingleFileGenerationResult.Refused(EntryGenerationRefusal.TargetHasContent(target)), result)
        assertEquals("", fs.readText(target))
        assertTrue(runner.requests.isEmpty())
    }

    // covers: 論点6
    @Test
    fun `空のファイルの未保存の編集がコメントだけなら保存してから仮のファイルを書く`() {
        fs.write(target, "")
        effects.typeUnsaved(target, "// todo\n")

        run()

        assertEquals(listOf("saveDocument", "mkdirs", "provisional", "json", "gradle", "refresh", "reload"), effects.log)
    }

    // covers: 論点16
    @Test
    fun `JSON の作り直しの間に仮の中身が外から書き換えられたら Gradle を走らせない`() {
        reloaded = { fs.write(target, "class Other\n"); reloadedWith(rows) }

        val result = run()

        assertEquals(SingleFileGenerationResult.Failed(EntryGenerationFailure.ChangedMeanwhile(target)), result)
        assertTrue(runner.requests.isEmpty())
        assertEquals("class Other\n", fs.readText(target))
        assertEquals(listOf(EntryGenerationFailure.ChangedMeanwhile(target)), effects.failures)
    }

    // covers: 論点16
    @Test
    fun `JSON の作り直しの間に仮のファイルを未保存で編集したら Gradle を走らせない`() {
        reloaded = { effects.typeUnsaved(target, effects.documents.getValue(target) + "class Typed\n"); reloadedWith(rows) }

        val result = run()

        assertEquals(SingleFileGenerationResult.Failed(EntryGenerationFailure.ChangedMeanwhile(target)), result)
        assertTrue(runner.requests.isEmpty())
    }

    @Test
    fun `作り直した一覧にテンプレートが無ければ失敗の理由を出す`() {
        reloaded = { reloadedWith(rows - controller) }

        val result = run()

        assertEquals(SingleFileGenerationResult.Failed(EntryGenerationFailure.TemplateGone), result)
        assertTrue(runner.requests.isEmpty())
        assertEquals(listOf<EntryGenerationFailure>(EntryGenerationFailure.TemplateGone), effects.failures)
    }

    @Test
    fun `作り直した定義で出力先が変わったら失敗の理由を出す`() {
        val moved = controller.copy(template = controller.template.copy(detail = controller.template.detail?.let { detail ->
            detail.copy(files = detail.files.map { it.copy(pattern = it.pattern.replace("controller/", "api/")) })
        }))
        reloaded = { reloadedWith(listOf(moved)) }

        val result = run()

        val newTarget = underRoot("src/main/kotlin/com/example/api/user/UserController.kt")
        assertEquals(SingleFileGenerationResult.Failed(EntryGenerationFailure.TargetMoved(newTarget)), result)
        assertTrue(runner.requests.isEmpty())
    }

    // covers: 論点16
    @Test
    fun `Gradle が失敗したら仮の中身と台帳の失敗が残り理由を出す`() {
        build = { failure }

        val result = run()

        assertTrue(result.toString(), result is SingleFileGenerationResult.Failed && result.failure is EntryGenerationFailure.Katachi)
        assertEquals(effects.documents.getValue(target), fs.readText(target))
        val entry = ledger.entryOf(target)
        assertTrue(entry.toString(), entry is LedgerEntry.Failed && entry.command.contains("--arg template=api.Controller"))
        assertEquals(1, effects.failures.size)
    }

    // covers: 論点16
    @Test
    fun `properties で Gradle が失敗した後に同じファイルでやり直せる`() {
        val properties = underRoot("src/main/kotlin/com/example/controller/user/User.properties")
        reloaded = { GenerationCatalogReload.Failed(LoadFailure.Cancelled) }
        run(request(properties))

        assertEquals(TargetState.OwnProvisional, runBlocking { generation.checkTarget(properties) })
        val retried = run(request(properties))
        assertTrue(retried.toString(), retried is SingleFileGenerationResult.Failed)
        assertEquals(2, catalog.reloads.size)
    }

    @Test
    fun `生成中のファイルには2本目を走らせない`() {
        ledger.markGenerating(target, controller.id, "./gradlew")

        assertEquals(TargetState.HasContent, runBlocking { generation.checkTarget(target) })
    }

    @Test
    fun `IDE がディレクトリを作れなければ何も書かずに理由を出す`() {
        effects.failing = setOf("mkdirs")

        val result = run()

        assertEquals(SingleFileGenerationResult.Failed(EntryGenerationFailure.Ide(EntryIdeAction.CREATE_DIRECTORIES)), result)
        assertFalse(fs.exists(target))
        assertEquals(null, ledger.entryOf(target))
        assertTrue(catalog.reloads.isEmpty())
    }

    @Test
    fun `出力先の決まらないテンプレートは仮のファイルを作らず onExisting=fail で走らせ書いたファイルを開く`() {
        build = { success(target to generated) }

        val result = run(request().copy(target = null))

        assertEquals(SingleFileGenerationResult.Generated(listOf(target)), result)
        assertFalse(effects.log.contains("provisional"))
        assertEquals("fail", runner.requests.single().tasks.single().args.single { it.first == "onExisting" }.second)
        assertEquals(listOf(target), effects.base.opened)
    }
}
