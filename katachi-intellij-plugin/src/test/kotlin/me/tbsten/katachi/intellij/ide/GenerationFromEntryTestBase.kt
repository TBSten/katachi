package me.tbsten.katachi.intellij.ide

import com.intellij.notification.Notification
import com.intellij.notification.Notifications
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.replaceService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import me.tbsten.katachi.intellij.data.NioProjectFileSystem
import me.tbsten.katachi.intellij.data.generate.SingleFileGenerationRequest
import me.tbsten.katachi.intellij.data.generate.SingleFileGenerationResult
import me.tbsten.katachi.intellij.data.gradle.GradleTaskRunner
import me.tbsten.katachi.intellij.data.placement.PlacementIndexState
import me.tbsten.katachi.intellij.model.KatachiModule
import me.tbsten.katachi.intellij.model.ModuleTemplate
import me.tbsten.katachi.intellij.presentation.entry.EntryOrigin
import me.tbsten.katachi.intellij.testing.rowOfTemplate
import me.tbsten.katachi.intellij.testing.rowsOf
import java.nio.file.Files
import java.nio.file.Path
import java.util.Collections

/**
 * The generation from an entry through the real service, `IdeEffectsImpl`, VFS and editors, with
 * T1's [DiskGradleRunner]: `sample-jvm-with-captures` in `:arch-a`, whose directory is katachi's
 * project root (a `.git` in it), so the files it generates sit in the definition's own `src/`.
 */
internal abstract class GenerationFromEntryTestBase : EntryServiceTestBase() {
    protected val archDir: Path get() = moduleDirOf(":arch-a")
    protected val sourceRoot: Path get() = archDir.resolve("src/main/kotlin")

    /** Exists; `user/` under it does not. */
    protected val controllerDir: Path get() = sourceRoot.resolve("com/example/controller")
    protected val target: Path get() = controllerDir.resolve("user/UserController.kt")
    protected val generated = "package com.example.controller.user\n\nclass UserController\n"

    /** What [IdeEffectsImpl] runs first in each SDK call of the generation; throw to play the IDE failing. */
    @Volatile protected var probe: (EntrySdkCall) -> Unit = {}

    protected val balloons: MutableList<Notification> = Collections.synchronizedList(mutableListOf())

    override fun setUp() {
        super.setUp()
        gradle.fixtureOf["arch-a"] = FIXTURE
        Files.createDirectories(archDir.resolve(".git"))
        Files.createDirectories(controllerDir)
        LocalFileSystem.getInstance().refreshAndFindFileByNioFile(controllerDir) ?: throw AssertionError("not in VFS: $controllerDir")
        project.messageBus.connect(testRootDisposable).subscribe(
            Notifications.TOPIC,
            object : Notifications {
                override fun notify(notification: Notification) {
                    if (notification.groupId == KatachiNotifications.GROUP_ID) balloons += notification
                }
            },
        )
    }

    /** A service as an entry creates it, whose IDE effects run [probe]. */
    protected fun install(runner: GradleTaskRunner = gradle): KatachiProjectService {
        val ports = KatachiPorts({ synced }, runner, NioProjectFileSystem, IdeEffectsImpl(project) { probe(it) }, importInProgress = { false })
        return KatachiProjectService(project, scope, ports).also { created ->
            project.replaceService(KatachiProjectService::class.java, created, testRootDisposable)
            service = created
        }
    }

    /** [install], then load the list once, as the notification would have before [Create]. */
    protected fun loaded(runner: GradleTaskRunner = gradle): KatachiProjectService {
        install(runner)
        service.ensureLoaded()
        waitForIndex("ready") { it is PlacementIndexState.Ready }
        settle()
        return service
    }

    /** `api.Controller` as `:arch-a` has it, whether or not the service loaded it yet. */
    protected fun controller(): ModuleTemplate = rowsOf(FIXTURE, KatachiModule(":arch-a", archDir, root, "project")).rowOfTemplate("api.Controller")

    /** What the dialog hands over for `api.Controller` with resource=user, name=User. */
    protected fun request(to: Path = target, template: ModuleTemplate = controller()): SingleFileGenerationRequest =
        SingleFileGenerationRequest(template, EntryOrigin.EditorFile(to), listOf("resource" to "user", "name" to "User"), to)

    /** A generation of the fake Gradle that writes [text] to [target]. */
    protected fun writes(text: String = generated, gate: CompletableDeferred<Unit>? = null): PlannedGeneration =
        PlannedGeneration(gate = gate) { mapOf(target to text) }

    @OptIn(ExperimentalCoroutinesApi::class)
    protected fun <T> Deferred<T>.awaitPumping(): T {
        PlatformTestUtil.waitWithEventsDispatching({ "the generation finishes" }, { isCompleted }, 10)
        return getCompleted()
    }

    protected fun generate(request: SingleFileGenerationRequest = request()): SingleFileGenerationResult =
        service.generateFromEntry(request).awaitPumping()

    protected fun waitUntil(what: String, condition: () -> Boolean) {
        PlatformTestUtil.waitWithEventsDispatching(what, { condition() }, 10)
    }

    /** The balloon texts so far, once [count] came. */
    protected fun balloonTexts(count: Int = 1): List<String> {
        waitUntil("$count balloons") { balloons.size >= count }
        return balloons.map { it.content }
    }

    protected fun documentText(path: Path): String? {
        val file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path) ?: return null
        return FileDocumentManager.getInstance().getDocument(file)?.text
    }

    protected fun isUnsaved(path: Path): Boolean {
        val file = LocalFileSystem.getInstance().findFileByNioFile(path) ?: return false
        return FileDocumentManager.getInstance().isFileModified(file)
    }

    companion object {
        const val FIXTURE: String = "sample-jvm-with-captures"
    }
}
