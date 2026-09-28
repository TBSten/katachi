package me.tbsten.katachi.intellij.ide.dialog

import kotlinx.coroutines.Dispatchers
import me.tbsten.katachi.intellij.data.generate.TargetState
import me.tbsten.katachi.intellij.model.ModuleTemplate
import me.tbsten.katachi.intellij.presentation.entry.EntryOrigin
import me.tbsten.katachi.intellij.presentation.entry.GenerateDialogRequest
import me.tbsten.katachi.intellij.testing.FakeCaptureSeedPort
import me.tbsten.katachi.intellij.testing.capture
import me.tbsten.katachi.intellij.testing.file
import me.tbsten.katachi.intellij.testing.module
import me.tbsten.katachi.intellij.testing.newMenuDirectory
import me.tbsten.katachi.intellij.testing.stringParam
import me.tbsten.katachi.intellij.testing.template
import java.nio.file.Path

/**
 * A dialog's world for the platform tests of D4: one definition with a template that has two
 * captures and a parameter, the checks recorded, no Gradle. Everything runs unconfined, so the
 * dialog's coroutines finish inside the call that started them.
 */
internal class DialogWorld(
    origin: EntryOrigin = newMenuDirectory("feature/home/ui"),
    seeds: Map<String, String> = mapOf("feature" to "home"),
) {
    val architecture = module(":arch-a")

    val screen = ModuleTemplate(
        architecture,
        template(
            "ui.Screen",
            parameters = listOf(stringParam("title")),
            captures = listOf(capture("feature"), capture("name")),
            files = listOf(file("x.kt", path = null, pattern = "feature/\${feature}/ui/\${name}Screen.kt")),
        ),
    )

    val request = GenerateDialogRequest(origin, screen.id, seeds)

    /** What [Generate]'s check finds at a path; a path not in it is [TargetState.Absent]. */
    val existing = mutableMapOf<Path, TargetState>()
    val checks = mutableListOf<Path?>()

    fun environment(request: GenerateDialogRequest = this.request) = GenerateDialogEnvironment(
        request = request,
        candidates = listOf(screen),
        seeds = FakeCaptureSeedPort(),
        checkTarget = { target ->
            checks += target
            target?.let { existing[it] } ?: TargetState.Absent
        },
        rootOf = { it.linkedRootPath },
    )

    fun dialog(project: com.intellij.openapi.project.Project, request: GenerateDialogRequest = this.request): KatachiGenerateDialog =
        KatachiGenerateDialog(
            project,
            environment(request),
            uiContext = Dispatchers.Unconfined,
            checkContext = Dispatchers.Unconfined,
            settle = {},
        )
}
