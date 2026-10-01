package me.tbsten.katachi.intellij.preview

import java.io.File

/** The dialog PNGs written, and what the mechanical gates say about them. */
internal class DialogOutcome(val fileNames: Set<String>, val gateFailures: List<String>)

private val dialogThemes = listOf("light" to false, "dark" to true)

private fun dialogFileNameOf(scenario: DialogScenario, theme: String) = "preview-dialog-${scenario.name}-$theme.png"

/** Every dialog PNG the scenarios expect, so that the file set gate knows them before they are drawn. */
internal fun expectedDialogFiles(): Set<String> =
    dialogScenarios.flatMap { s -> dialogThemes.map { (theme, _) -> dialogFileNameOf(s, theme) } }.toSet()

/**
 * Draws every dialog scenario in light and dark into [outDir], and runs the gates that eyeballing
 * must not be the only check of: no part sticks out of the window or overlaps another, no text is
 * cut, and nothing touches the window edge.
 */
internal fun renderDialogScenarios(outDir: File): DialogOutcome {
    val names = mutableSetOf<String>()
    val failures = mutableListOf<String>()
    for (scenario in dialogScenarios) {
        for ((theme, dark) in dialogThemes) {
            val rendered = renderDialog(scenario, dark)
            val name = dialogFileNameOf(scenario, theme)
            val file = File(outDir, name).apply { writeBytes(rendered.png) }
            names += name
            PreviewChecks.layoutProblems(scenario.size.width, scenario.size.height, rendered.nodes).forEach { failures += "$name: $it" }
            PreviewChecks.fontProblems(rendered.texts, PreviewFonts::covers).forEach { failures += "$name: $it" }
            if (PreviewChecks.edgeTouched(file)) failures += "$name: something is drawn on the window edge (cut off there)"
        }
    }
    return DialogOutcome(names, failures)
}
