package me.tbsten.katachi.intellij.ide.authoring.errors

import com.intellij.codeInsight.daemon.LineMarkerProviders
import com.intellij.lang.annotation.HighlightSeverity
import me.tbsten.katachi.intellij.ide.gutter.annotator.DrawnAnnotation
import me.tbsten.katachi.intellij.model.DefinitionErrorModel
import me.tbsten.katachi.intellij.model.DefinitionErrorsEvent
import org.jetbrains.kotlin.idea.KotlinLanguage
import kotlin.random.Random

/**
 * A sequence of what happens to a definition (a load rejected, one that reads it, a change, an edit, a load that starts) with a
 * definition file open: after every step the underlines are exactly those the model of the mistakes says, with the colour and the
 * `(stale) ` mark of the moment (ide-authoring design 2.5). Run once with the test restarting the daemon, once leaving that to the
 * real [me.tbsten.katachi.intellij.ide.gutter.HighlightingRefresher], which must catch up with every change of the mistakes.
 */
internal class DefinitionErrorSequenceTest : DefinitionErrorTestBase() {
    /** What the model says is on screen: the mistakes of the last rejected load and whether the definition changed after it. */
    private class World {
        var mistakes: List<DefinitionErrorModel> = emptyList()
        var stale: Boolean = false
        var typedLines: Int = 0
    }

    private fun step(random: Random, world: World): String = when (random.nextInt(6)) {
        0 -> {
            val found = List(random.nextInt(1, 4)) { mistake(if (random.nextInt(5) == 0) null else random.nextInt(1, LINES + 1), "mistake ${random.nextInt(1000)}\nline two") }
            definitions.emitErrors(DefinitionErrorsEvent.LoadRejected(found, tick()))
            world.mistakes = found
            world.stale = false
            "rejected(${found.map { it.site?.line }})"
        }
        1 -> {
            definitions.emitErrors(DefinitionErrorsEvent.LoadOk(tick()))
            world.mistakes = emptyList()
            world.stale = false
            "ok"
        }
        2 -> {
            definitions.emitErrors(DefinitionErrorsEvent.DefinitionChanged)
            if (world.mistakes.isNotEmpty()) world.stale = true
            "changed"
        }
        3 -> {
            definitions.emitErrors(DefinitionErrorsEvent.LoadStarted)
            "started"
        }
        4 -> {
            definitions.emitErrors(DefinitionErrorsEvent.LoadFailedOther)
            "failedOther"
        }
        else -> {
            typeAtStart("// typed ${world.typedLines}\n")
            world.typedLines++
            "typed"
        }
    }

    private fun expected(world: World): List<Triple<Int, HighlightSeverity, Boolean>> {
        val lines = myFixture.editor.document.text.lines()
        return world.mistakes.mapNotNull { it.site?.line }
            .filter { it in 1..lines.size && lines[it - 1].isNotBlank() }
            .map { Triple(it, if (world.stale) HighlightSeverity.WEAK_WARNING else HighlightSeverity.ERROR, world.stale) }
            .sortedBy { it.first }
    }

    private fun check(world: World, where: String, redraw: () -> List<DrawnAnnotation>) {
        val drawn = redraw()
        assertEquals(where, expected(world), drawn.sortedBy { it.line }.map { Triple(it.line!!, it.severity, it.description.startsWith("(stale) ")) })
    }

    private fun runSequences(sequences: Int, localFiles: Boolean, redraw: () -> List<DrawnAnnotation>) {
        if (localFiles) useLocalFiles()
        repeat(sequences) { index ->
            val sequenceSeed = 20261018L + index
            val random = Random(sequenceSeed)
            val world = World()
            openDefinition()
            rejected()
            val log = mutableListOf("open")
            check(world, "seed=$sequenceSeed open", redraw)
            repeat(10) {
                log += step(random, world)
                check(world, "seed=$sequenceSeed ${log.joinToString(" → ")}", redraw)
            }
        }
    }

    fun `test 読み込みの失敗 成功 変更 書き込み の列を流しても 下線は誤りの今の行に付き 色と stale を正しく言う`() {
        runSequences(System.getProperty("katachi.authoring.sequences")?.toInt() ?: 12, localFiles = false, ::annotated)
    }

    fun `test 同じ列を 描き直しを本物の HighlightingRefresher に任せて流しても 下線は誤りの変化に追いつく`() {
        // The test never restarts the daemon: only the refresher's restart (the mistakes changed) or an edit draws a new answer.
        runSequences(System.getProperty("katachi.authoring.refresherSequences")?.toInt() ?: 3, localFiles = true, ::annotatedByRefresher)
    }

    fun `test ガターの目印が無くても 下線の注釈だけで HighlightingRefresher は誤りの変化に追いつく`() {
        // The refresher is followed by the first provider that runs; here only the annotator is left, so it must follow by itself.
        LineMarkerProviders.getInstance().removeExplicitExtension(KotlinLanguage.INSTANCE, provider)
        runSequences(2, localFiles = true, ::annotatedByRefresher)
    }
}
