package me.tbsten.katachi.intellij.ide.dialog

import com.intellij.openapi.progress.ProcessCanceledException
import me.tbsten.katachi.intellij.AnalysisTestBase
import me.tbsten.katachi.intellij.data.generate.SingleFileGenerationRequest
import me.tbsten.katachi.intellij.ide.KatachiBundle
import me.tbsten.katachi.intellij.ide.entry.EntryEffectsImpl
import me.tbsten.katachi.intellij.presentation.entry.GenerateDialogRequest
import me.tbsten.katachi.intellij.testing.singleFileRequest
import java.util.concurrent.CancellationException

/**
 * `EntryEffectsImpl` with the IDE's modal parts swapped (`GenerateDialogs.setTestAnswer`, the
 * settings seam): what is asked of it, what it hands on, and that an SDK failure is a balloon, not a
 * failed click, while control flow is thrown again.
 */
internal class EntryEffectsImplTest : AnalysisTestBase() {
    private val world = DialogWorld()
    private val balloons = mutableListOf<String>()
    private val generated = mutableListOf<SingleFileGenerationRequest>()

    private fun effects(
        showSettings: () -> Unit = {},
        environmentOf: (GenerateDialogRequest) -> GenerateDialogEnvironment = { world.environment(it) },
    ) = EntryEffectsImpl(
        project,
        notify = { balloons += it },
        showSettings = { showSettings() },
        environmentOf = { _, request -> environmentOf(request) },
        generate = { generated += it },
    )

    // covers: 論点14
    fun `test 設定を開くときは katachi のページを開く関数を呼ぶ`() {
        var opened = 0

        effects(showSettings = { opened++ }).openSettings()

        assertEquals(1, opened)
        assertEquals(emptyList<String>(), balloons)
    }

    // covers: 論点14
    fun `test 設定を開く呼び出しが例外を投げても操作は失敗せず balloon で知らせる`() {
        effects(showSettings = { throw IllegalStateException("no such page") }).openSettings()

        assertEquals(listOf(KatachiBundle.message("entry.openSettingsFailed", "no such page")), balloons)
    }

    // covers: 論点14
    fun `test 設定を開く呼び出しの ProcessCanceledException と CancellationException は伝わる`() {
        assertThrows(ProcessCanceledException::class.java) { effects(showSettings = { throw ProcessCanceledException() }).openSettings() }
        assertThrows(CancellationException::class.java) { effects(showSettings = { throw CancellationException("cancelled") }).openSettings() }
        assertEquals(emptyList<String>(), balloons)
    }

    // covers: 論点0
    fun `test ダイアログの結果は生成に渡り開くときの要求はそのまま届く`() {
        val answer = singleFileRequest()
        var asked: GenerateDialogRequest? = null
        GenerateDialogs.setTestAnswer({ asked = it; answer }, testRootDisposable)

        effects().openGenerateDialog(world.request)

        assertEquals(world.request, asked)
        assertEquals(listOf(answer), generated)
        assertEquals(emptyList<String>(), balloons)
    }

    // covers: 論点0
    fun `test ダイアログをキャンセルすると生成は始まらず何も知らせない`() {
        GenerateDialogs.setTestAnswer({ null }, testRootDisposable)

        effects().openGenerateDialog(world.request)

        assertEquals(emptyList<SingleFileGenerationRequest>(), generated)
        assertEquals(emptyList<String>(), balloons)
    }

    // covers: 論点17
    fun `test ダイアログを作る途中で例外を投げても操作は失敗せず balloon で知らせ生成は始まらない`() {
        effects(environmentOf = { throw IllegalStateException("no project service") }).openGenerateDialog(world.request)

        assertEquals(listOf(KatachiBundle.message("entry.openDialogFailed", "no project service")), balloons)
        assertEquals(emptyList<SingleFileGenerationRequest>(), generated)
    }

    // covers: 論点17
    fun `test ダイアログを作る途中の ProcessCanceledException と CancellationException は伝わる`() {
        assertThrows(ProcessCanceledException::class.java) {
            effects(environmentOf = { throw ProcessCanceledException() }).openGenerateDialog(world.request)
        }
        assertThrows(CancellationException::class.java) {
            effects(environmentOf = { throw CancellationException("cancelled") }).openGenerateDialog(world.request)
        }
        assertEquals(emptyList<String>(), balloons)
        assertEquals(emptyList<SingleFileGenerationRequest>(), generated)
    }
}
