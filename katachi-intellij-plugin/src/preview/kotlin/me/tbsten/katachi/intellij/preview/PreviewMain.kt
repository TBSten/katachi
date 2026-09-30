@file:OptIn(InternalComposeUiApi::class) // renderComposeScene

package me.tbsten.katachi.intellij.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.renderComposeScene
import androidx.compose.runtime.CompositionLocalProvider
import me.tbsten.katachi.intellij.presentation.EnglishKatachiStrings
import me.tbsten.katachi.intellij.presentation.JapaneseKatachiStrings
import me.tbsten.katachi.intellij.presentation.KatachiStrings
import me.tbsten.katachi.intellij.presentation.uiStateOf
import me.tbsten.katachi.intellij.ui.KatachiToolWindowContent
import me.tbsten.katachi.intellij.ui.LocalStaticRendering
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.system.exitProcess

/**
 * The headless preview harness: renders the tool window's Jewel/Compose UI to PNGs without
 * starting an IDE, writes a gallery, and syncs or compares the VRT golden.
 *
 * Run through Gradle:
 * - `updatePreview` — render every PNG, write the gallery, force-sync the golden (snapshots/preview)
 * - `verifyPreview` — render every PNG and compare with the golden; any changed / new / missing PNG
 *   fails the run, with a before/after report at build/preview/report/index.html
 *
 * Run `verifyPreview` first; update the golden only after a human has approved the change.
 */

/** Every state of the screen spec (PreviewScenarios.kt), each docked narrow and wide. */
private val scenarios: List<Scenario> = statusScenarios + listScenarios + generationScenarios + longScenario + revealScenarios

/** One PNG per scenario × layout × language × theme. */
private data class Render(val scenario: Scenario, val layout: String, val width: Int, val height: Int)

private val renders: List<Render> = scenarios.flatMap { s ->
    listOf(
        Render(s, "narrow", NARROW_WIDTH, s.narrowHeight),
        Render(s, "wide", WIDE_WIDTH, s.wideHeight),
    )
}

/**
 * The tool window's languages. The file name of Japanese has no language, as the dialog's scenarios
 * (Japanese unless named `english-`), and English carries `-en`.
 */
private enum class Language(val suffix: String, val strings: KatachiStrings, val themes: List<Pair<String, Boolean>>) {
    Japanese("", JapaneseKatachiStrings, listOf("light" to false, "dark" to true)),

    // Light only: a theme changes the colors and never where the text wraps, which is what English changes.
    English("-en", EnglishKatachiStrings, listOf("light" to false)),
}

fun main(args: Array<String>) {
    // Also passed as Gradle jvmArgs; set again so that a plain run from the IDE works too.
    System.setProperty("java.awt.headless", "true")
    System.setProperty("skiko.renderApi", "SOFTWARE")

    val mode = args.firstOrNull()
    if (mode != "update" && mode != "verify") {
        System.err.println("usage: PreviewMainKt <update|verify>  (gradle: updatePreview / verifyPreview)")
        exitProcess(2)
    }

    // Relative to the JavaExec working directory, which is the plugin project directory.
    val outDir = File("build/preview")
    val goldenDir = File("snapshots/preview") // the committed golden

    // Clean managed outputs first, so a renamed or removed scenario leaves no stale PNG behind.
    PreviewChecks.cleanManagedOutputs(outDir)
    outDir.mkdirs()

    val expected = renders.flatMap { r ->
        Language.entries.flatMap { language -> language.themes.map { (theme, _) -> fileNameOf(r, language, theme) } }
    }.toSet() + expectedDialogFiles()
    for (render in renders) {
        for (language in Language.entries) {
            for ((theme, dark) in language.themes) {
                renderScenario(render, language.strings, dark, File(outDir, fileNameOf(render, language, theme)))
            }
        }
    }
    val dialogs = renderDialogScenarios(outDir)
    writeGallery(outDir, expected.sorted())

    // Mechanical gates, so that eyeballing the PNGs is not the only check.
    val gateFailures = buildList {
        addAll(dialogs.gateFailures)
        addAll(PreviewChecks.unexpectedFileSet(outDir, expected))
        addAll(
            PreviewChecks.transparentCornerPngs(expected.sorted().map { File(outDir, it) }).map {
                "PNG with a transparent corner: ${it.name} — paint the render root with the theme surface"
            },
        )
    }
    if (gateFailures.isNotEmpty()) {
        System.err.println("preview gates failed (${gateFailures.size}):")
        gateFailures.forEach { System.err.println("  - $it") }
        exitProcess(1)
    }

    when (mode) {
        "update" -> {
            PreviewChecks.syncGolden(outDir, goldenDir, expected)
            println("golden updated: ${goldenDir.toPath().toAbsolutePath().toUri()} (${expected.size} PNGs). Review the diff before committing.")
            println("gallery: ${File(outDir, "index.html").toPath().toAbsolutePath().toUri()}")
        }
        "verify" -> {
            val diff = PreviewChecks.diffAgainstGolden(outDir, goldenDir, expected)
            if (diff.isEmpty()) {
                println("verifyPreview OK: matches the golden (${expected.size} PNGs)")
            } else {
                val report = writeReport(outDir, goldenDir, diff)
                System.err.println("verifyPreview failed: differs from the golden")
                diff.changed.forEach { System.err.println("  changed: $it") }
                diff.new.forEach { System.err.println("  new (not in the golden): $it") }
                diff.missing.forEach { System.err.println("  missing (only in the golden): $it") }
                System.err.println("Before/after: ${report.toPath().toAbsolutePath().toUri()}. If the change is intended, run updatePreview after a human approves it.")
                exitProcess(1)
            }
        }
    }
}

private fun fileNameOf(render: Render, language: Language, theme: String) =
    "preview-${render.scenario.name}-${render.layout}${language.suffix}-$theme.png"

/** Standalone Jewel Int UI theme + renderComposeScene -> PNG. */
private fun renderScenario(render: Render, strings: KatachiStrings, dark: Boolean, out: File) {
    val ui = uiStateOf(render.scenario.state, strings, PREVIEW_NOW)
    val image = renderComposeScene(width = render.width, height = render.height) {
        IntUiTheme(isDark = dark) {
            CompositionLocalProvider(LocalStaticRendering provides true) {
                // Paint the whole root with the panel background; the transparent-corner gate needs it.
                Box(Modifier.fillMaxSize().background(JewelTheme.globalColors.panelBackground)) {
                    KatachiToolWindowContent(ui, onIntent = {})
                }
            }
        }
    }
    out.writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
}

/** Writes one page showing every PNG, for agents and humans to look at. */
private fun writeGallery(outDir: File, names: List<String>) {
    val rows = names.joinToString("\n") { name ->
        """<figure><img src="$name" alt="$name"><figcaption>$name</figcaption></figure>"""
    }
    File(outDir, "index.html").writeText(
        """
        <!doctype html>
        <meta charset="utf-8">
        <title>katachi preview gallery</title>
        <style>
            body { font-family: sans-serif; background: #808080; }
            figure { display: inline-block; margin: 8px; }
            img { display: block; border: 1px solid #333; image-rendering: pixelated; }
            figcaption { font-size: 12px; text-align: center; }
        </style>
        <h1>preview gallery</h1>
        $rows
        """.trimIndent(),
    )
}

/** The before/after report of a failed verify. Copies golden and actual under report/ so it stands alone. */
private fun writeReport(outDir: File, goldenDir: File, diff: PreviewChecks.GoldenDiff): File {
    val reportDir = File(outDir, "report").apply { mkdirs() }
    File(reportDir, "golden").mkdirs()
    File(reportDir, "actual").mkdirs()

    fun copied(sub: String, dir: File, name: String): String? {
        val src = File(dir, name)
        if (!src.isFile) return null
        src.copyTo(File(reportDir, "$sub/$name"), overwrite = true)
        return "$sub/$name"
    }

    fun row(name: String, status: String): String {
        val golden = copied("golden", goldenDir, name)
        val actual = copied("actual", outDir, name)
        fun cell(path: String?) = path?.let { """<img src="$it" alt="$it">""" } ?: "<em>(none)</em>"
        return """<tr><td>$name</td><td>$status</td><td>${cell(golden)}</td><td>${cell(actual)}</td></tr>"""
    }

    val rows = diff.changed.joinToString("\n") { row(it, "changed") } + "\n" +
        diff.new.joinToString("\n") { row(it, "new") } + "\n" +
        diff.missing.joinToString("\n") { row(it, "missing") }
    val report = File(reportDir, "index.html")
    report.writeText(
        """
        <!doctype html>
        <meta charset="utf-8">
        <title>verifyPreview report</title>
        <style>
            body { font-family: sans-serif; background: #808080; }
            table { border-collapse: collapse; }
            td, th { border: 1px solid #333; padding: 6px; vertical-align: top; }
            img { display: block; max-width: 480px; image-rendering: pixelated; }
        </style>
        <h1>verifyPreview report (golden vs actual)</h1>
        <table>
            <tr><th>png</th><th>status</th><th>golden (before)</th><th>actual (after)</th></tr>
            $rows
        </table>
        """.trimIndent(),
    )
    return report
}
