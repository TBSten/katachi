package me.tbsten.katachi.intellij.ide.gutter.annotator

import com.intellij.codeInsight.daemon.LineMarkerProviders
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.command.WriteCommandAction
import me.tbsten.katachi.intellij.ide.gutter.HighlightingRefresher
import me.tbsten.katachi.intellij.model.SarifLevel
import me.tbsten.katachi.intellij.model.ViolationModel
import me.tbsten.katachi.intellij.model.ViolationsState
import me.tbsten.katachi.intellij.testing.AnnotationFixtures
import me.tbsten.katachi.intellij.testing.DEFINITION_INSTANT
import org.jetbrains.kotlin.idea.KotlinLanguage
import kotlin.random.Random

/**
 * The annotations through sequences of what happens around an open file: a check ends (with results taken from the file as
 * it is), the user types above the declarations, adds, deletes and renames them, the project goes stale, the SARIF is
 * half written or deleted, the setting is switched. After each step the daemon draws again and the annotations must
 * agree with an oracle read off the editor and the last answer: the declaration's name wherever it is now, else the line the
 * check said if it still has text, else the banner; one step weaker when the check is old.
 *
 * One test restarts the daemon itself, the other leaves it to the real [HighlightingRefresher] (a state change reaches the open
 * file by itself). `-Dkatachi.gutter.sequences=N` runs N sequences.
 */
internal class ViolationAnnotatorSequenceTest : AnnotatorTestBase() {
    private val fileName = "Sequence.kt"

    override fun setUp() {
        super.setUp()
        // The user may switch the ◆ off in Gutter Icons: the annotations follow the services on their own then.
        LineMarkerProviders.getInstance().removeExplicitExtension(KotlinLanguage.INSTANCE, provider)
    }

    /** What the last check said of one violation; the lines are the ones it saw. */
    private sealed interface Said {
        data class Rejected(val name: String, val line: Int, val level: SarifLevel) : Said
        data class Missing(val level: SarifLevel) : Said
        data class Unchecked(val level: SarifLevel) : Said
        data object Unexpected : Said
    }

    private enum class Report { NotRun, Fresh, Stale, Unreadable, UnreadableWithoutBefore }

    private class World(var said: List<Said> = emptyList(), var report: Report = Report.NotRun, var counter: Int = 0, var shown: Boolean = true)

    private val text get() = myFixture.editor.document.text

    private fun classNames(): List<String> = Regex("""^class (\w+)""", RegexOption.MULTILINE).findAll(text).map { it.groupValues[1] }.toList()

    private fun edit(change: (String) -> String) {
        val before = text
        WriteCommandAction.runWriteCommandAction(project) { myFixture.editor.document.setText(change(before)) }
    }

    private fun violationsOf(said: List<Said>): List<ViolationModel> = said.map {
        when (it) {
            is Said.Rejected -> AnnotationFixtures.unsatisfied(pathOf(fileName), it.name, it.line, level = it.level)
            is Said.Missing -> AnnotationFixtures.missing(pathOf(fileName), level = it.level, declaredAt = AnnotationFixtures.RULE_AT)
            is Said.Unchecked -> AnnotationFixtures.unchecked(pathOf(fileName), it.level)
            Said.Unexpected -> AnnotationFixtures.unexpected(pathOf(fileName))
        }
    }

    private fun emitReport(world: World) {
        val ready = ViolationsState.Ready(violationsOf(world.said), DEFINITION_INSTANT.plusSeconds(world.said.size.toLong()), stale = world.report == Report.Stale, paths = listOf(LIGHT_SARIF))
        when (world.report) {
            Report.NotRun -> violations.emit(ViolationsState.NotRun)
            Report.Fresh, Report.Stale -> violations.emit(ready)
            Report.Unreadable -> violations.emit(ViolationsState.Unreadable(LIGHT_SARIF, "cut off", ready))
            Report.UnreadableWithoutBefore -> violations.emit(ViolationsState.Unreadable(LIGHT_SARIF, "cut off", previous = null))
        }
    }

    private fun step(random: Random, world: World): String = when (random.nextInt(10)) {
        0 -> "type blank lines at the top".also { edit { "\n\n$it" } }
        1 -> "add class".also { val name = "Added${world.counter++}"; edit { "$it\nclass $name\n" } }
        2 -> {
            val target = classNames().randomOrNull(random)
            if (target == null) "delete a class (none)" else "delete $target".also { edit { it.replace(Regex("""^class $target\b.*$""", RegexOption.MULTILINE), "") } }
        }
        3 -> {
            val target = classNames().randomOrNull(random)
            if (target == null) "rename a class (none)" else "rename $target".also { val renamed = "${target}R${world.counter++}"; edit { it.replace(Regex("""^class $target\b""", RegexOption.MULTILINE), "class $renamed") } }
        }
        4, 5 -> {
            val classes = classNames()
            val picked = classes.filter { random.nextInt(3) == 0 }
            // One level per check: the platform hides a warning under a file-level error banner (the banner covers the whole file), which this test does not model.
            val level = if (random.nextInt(4) == 0) SarifLevel.Warning else SarifLevel.Error
            world.said = picked.map { Said.Rejected(it, lineOf(it), level) } +
                listOfNotNull(Said.Missing(level).takeIf { random.nextInt(4) == 0 }, Said.Unchecked(level).takeIf { random.nextInt(5) == 0 }, Said.Unexpected.takeIf { random.nextInt(5) == 0 })
            world.report = Report.Fresh
            "a check ended with ${world.said}".also { emitReport(world) }
        }
        6 -> "the project changed after the check".also { if (world.report == Report.Fresh) { world.report = Report.Stale; emitReport(world) } }
        7 -> "the SARIF is half written".also { if (world.report == Report.Fresh || world.report == Report.Stale) { world.report = Report.Unreadable; emitReport(world) } }
        8 -> "the report is deleted".also { world.report = Report.NotRun; emitReport(world) }
        else -> "the setting is switched".also { world.shown = !world.shown; showViolations = world.shown; HighlightingRefresher.getInstance(project).redrawNow("the setting changed") }
    }

    private fun lineOf(name: String): Int = text.lines().indexOfFirst { Regex("""^class $name\b""").containsMatchIn(it) } + 1

    /** What the user should see, worked out from the editor's text and what the check last said. */
    private fun expected(world: World): List<Triple<String?, Int?, String>> {
        if (!world.shown) return emptyList()
        val stale = world.report == Report.Stale || world.report == Report.Unreadable
        if (world.report == Report.NotRun || world.report == Report.UnreadableWithoutBefore) return emptyList()
        val lines = text.lines()
        return world.said.mapNotNull { said ->
            val level = when (said) {
                is Said.Rejected -> said.level
                is Said.Missing -> said.level
                is Said.Unchecked -> said.level
                Said.Unexpected -> SarifLevel.Error
            }
            val severity = when {
                level == SarifLevel.Warning -> if (stale) HighlightSeverity.WEAK_WARNING else HighlightSeverity.WARNING
                else -> if (stale) HighlightSeverity.WARNING else HighlightSeverity.ERROR
            }
            when (said) {
                Said.Unexpected -> null
                is Said.Missing, is Said.Unchecked -> Triple(null, null, severity.toString())
                is Said.Rejected -> {
                    val now = lineOf(said.name)
                    val fallback = lines.getOrNull(said.line - 1)?.trim()?.takeIf { it.isNotEmpty() }
                    when {
                        now > 0 -> Triple(said.name, now, severity.toString())
                        fallback != null -> Triple(fallback, said.line, severity.toString())
                        else -> Triple(null, null, severity.toString())
                    }
                }
            }
        }.sortedWith(compareBy({ it.second ?: 0 }, { it.first.orEmpty() }, { it.third }))
    }

    private fun check(world: World, where: String, redraw: () -> List<DrawnAnnotation>) {
        val drawn = redraw()
        val seen = drawn.map { Triple(it.text, it.line, it.severity.toString()) }.sortedWith(compareBy({ it.second ?: 0 }, { it.first.orEmpty() }, { it.third }))
        assertEquals("$where\ntext:\n$text", expected(world), seen)
        val stale = world.report == Report.Stale || world.report == Report.Unreadable
        for (annotation in drawn) {
            assertEquals("$where: ${annotation.description}", stale, annotation.description.startsWith("(stale) "))
        }
    }

    private fun runSequences(sequences: Int, localFiles: Boolean, redraw: () -> List<DrawnAnnotation>) {
        if (localFiles) useLocalFiles()
        repeat(sequences) { index ->
            val sequenceSeed = 20261005L + index
            val random = Random(sequenceSeed)
            violations.emit(ViolationsState.NotRun)
            showViolations = true
            open(fileName, "package a\n\nclass A0\n\nclass A1\n\nclass A2\n")
            val world = World()
            val log = mutableListOf("open")
            check(world, "seed=$sequenceSeed open", redraw)
            repeat(10) {
                log += step(random, world)
                check(world, "seed=$sequenceSeed ${log.joinToString(" → ")}", redraw)
            }
        }
    }

    fun `test 検査の終わり 編集 古くなる 書きかけ 消える 設定の切り替え の列を流しても 注釈は宣言の今の場所に付き 色と stale を正しく言う`() {
        runSequences(System.getProperty("katachi.gutter.sequences")?.toInt() ?: 12, localFiles = false, ::annotated)
    }

    fun `test 同じ列を 描き直しを本物の HighlightingRefresher に任せて流しても 注釈は状態の変化に追いつく`() {
        // The test never restarts the daemon: only the refresher's restart (a state of the violations changed, or the setting's redraw) draws a new answer.
        runSequences(System.getProperty("katachi.gutter.refresherSequences")?.toInt() ?: 3, localFiles = true, ::annotatedByRefresher)
    }

    private fun <T> List<T>.randomOrNull(random: Random): T? = if (isEmpty()) null else this[random.nextInt(size)]
}
