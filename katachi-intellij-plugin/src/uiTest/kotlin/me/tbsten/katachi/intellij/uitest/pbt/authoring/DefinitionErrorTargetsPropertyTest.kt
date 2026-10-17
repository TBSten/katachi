package me.tbsten.katachi.intellij.uitest.pbt.authoring

import io.kotest.property.Arb
import io.kotest.property.PropTest
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import io.kotest.property.toPropTestConfig
import kotlinx.coroutines.runBlocking
import me.tbsten.katachi.intellij.model.DeclarationSiteRef
import me.tbsten.katachi.intellij.model.DefinitionErrorModel
import me.tbsten.katachi.intellij.model.DefinitionErrorPhase
import me.tbsten.katachi.intellij.model.DefinitionErrors
import me.tbsten.katachi.intellij.model.DefinitionProblem
import me.tbsten.katachi.intellij.model.DefinitionProblemKind
import me.tbsten.katachi.intellij.model.ModuleId
import me.tbsten.katachi.intellij.model.TemplateRef
import me.tbsten.katachi.intellij.presentation.authoring.DefinitionErrorTargets
import me.tbsten.katachi.intellij.presentation.authoring.DefinitionMarkSource
import me.tbsten.katachi.intellij.presentation.gutter.AnnotationSeverity
import me.tbsten.katachi.intellij.testing.definitionModel
import me.tbsten.katachi.intellij.testing.definitionSnapshot
import me.tbsten.katachi.intellij.testing.layoutEntry
import me.tbsten.katachi.intellij.testing.module
import me.tbsten.katachi.intellij.testing.roleModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import kotlin.random.Random

/**
 * Which lines the mistakes and problems of a definition underline, over generated mistakes, problems and sites
 * (ide-authoring design 2.4, P-2):
 *
 * - every line is inside the document, whatever line a site names
 * - the colour follows the table: a mistake is an error (a weak warning once stale), a problem a warning (a weak warning once stale)
 * - a site that is not in the file is never drawn, and the notification still counts it
 * - a mistake is drawn once at most, and its index points at the mistake that was drawn
 */
class DefinitionErrorTargetsPropertyTest {
    private val seed = System.getProperty("katachi.pbt.seed")?.toLong() ?: 20261018L
    private val scale = System.getProperty("katachi.pbt.scale")?.toDouble() ?: 1.0
    private val config get() = PropTest(seed = seed, iterations = (400 * scale).toInt().coerceAtLeast(1)).toPropTestConfig()

    private val arch: ModuleId = module(":arch").id
    private val problemKinds = DefinitionProblemKind.entries

    private fun site(random: Random): DeclarationSiteRef? = when (random.nextInt(4)) {
        0 -> null
        1 -> DeclarationSiteRef("Other.kt", random.nextInt(1, 60))
        else -> DeclarationSiteRef("ScreenRole.kt", random.nextInt(-3, 80))
    }

    private fun mistake(random: Random, n: Int) = DefinitionErrorModel(
        arch, if (random.nextBoolean()) DefinitionErrorPhase.Processor else DefinitionErrorPhase.EntryPoint, null, "me.tbsten.katachi.E$n", "message $n\nsecond", site(random),
    )

    private fun problem(random: Random, n: Int): DefinitionProblem {
        val kind = problemKinds[random.nextInt(problemKinds.size)]
        return DefinitionProblem(kind, kind.name, "problem $n", "t.T$n", site(random), null)
    }

    private val here: (ModuleId, DeclarationSiteRef) -> Int? = { _, site -> site.line.takeIf { site.fileName == "ScreenRole.kt" } }

    @Test
    fun `どんな誤りと problem と行でも 行は文書に収まり 色は表どおり 範囲外の場所は描かない`() = runBlocking<Unit> {
        checkAll(config, Arb.long()) { caseSeed ->
            val random = Random(caseSeed)
            val mistakes = List(random.nextInt(0, 6)) { mistake(random, it) }
            val errorsStale = random.nextBoolean()
            val definitionStale = random.nextBoolean()
            val errors = DefinitionErrors(mistakes, Instant.parse("2026-10-18T00:00:00Z"), errorsStale)
            val problems = List(random.nextInt(0, 6)) { problem(random, it) }
            val templateAt = if (random.nextBoolean()) "t.T0" to random.nextInt(1, 70) else null
            val role = roleModel("t.T", layoutEntry("src/*.kt").let { entry ->
                if (templateAt == null) entry else entry.copy(template = TemplateRef(templateAt.first, null, null, DeclarationSiteRef("ScreenRole.kt", templateAt.second)))
            })
            val snapshot = definitionSnapshot(definitionModel(role).copy(problems = problems), module(":arch"))
            val lineCount = random.nextInt(0, 50)

            val marks = DefinitionErrorTargets.marksOf(errors, listOf(snapshot), definitionStale, here, lineCount)

            val label = "seed=$caseSeed lineCount=$lineCount"
            for (mark in marks) {
                assertTrue("$label line ${mark.line}", mark.line in 1..lineCount)
                val expected = when (mark.source) {
                    DefinitionMarkSource.Error -> if (errorsStale) AnnotationSeverity.WeakWarning else AnnotationSeverity.Error
                    DefinitionMarkSource.Problem -> if (definitionStale) AnnotationSeverity.WeakWarning else AnnotationSeverity.Warning
                }
                assertEquals("$label ${mark.source}", expected, mark.severity)
                assertTrue("$label ${mark.hover}", mark.hover.startsWith(if (mark.severity == AnnotationSeverity.WeakWarning) "(stale) katachi: " else "katachi: "))
            }
            val errorMarks = marks.filter { it.source == DefinitionMarkSource.Error }
            assertEquals("$label an index is used once", errorMarks.size, errorMarks.map { it.errorIndex }.toSet().size)
            for (mark in errorMarks) {
                val drawn = mistakes[mark.errorIndex!!]
                assertEquals("$label the mistake drawn is in the file", "ScreenRole.kt", drawn.site?.fileName)
            }
            // A mistake whose site is in the file and on a real line is drawn: nothing is lost on the way.
            val expectedDrawn = mistakes.indices.filter { i -> mistakes[i].site?.let { it.fileName == "ScreenRole.kt" && it.line >= 1 && lineCount >= 1 } == true }
            assertEquals("$label", expectedDrawn, errorMarks.map { it.errorIndex })
            // The notification counts every mistake, drawn or not.
            val notification = DefinitionErrorTargets.notificationOf(errors)
            assertEquals("$label", if (mistakes.isEmpty()) null else mistakes.size - 1, notification?.more)
        }
    }
}
