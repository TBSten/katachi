package me.tbsten.katachi.intellij.ide

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.PlatformTestUtil
import kotlinx.coroutines.CompletableDeferred
import me.tbsten.katachi.intellij.data.generate.EntryGenerationRefusal
import me.tbsten.katachi.intellij.data.placement.PlacementIndexState
import me.tbsten.katachi.intellij.ide.newmenu.KatachiNewGroup
import me.tbsten.katachi.intellij.ide.newmenu.TemplateItemAction
import me.tbsten.katachi.intellij.ide.notification.link
import me.tbsten.katachi.intellij.presentation.entry.EntryOrigin
import me.tbsten.katachi.intellij.presentation.entry.LedgerEntry
import me.tbsten.katachi.intellij.testing.ContractFixtures
import java.nio.file.Files
import java.nio.file.Path

/**
 * From the entry to the generated file, all of it real but Gradle (plan chapter 1 "生成の流れ", decisions 8, 12, 16):
 * the notification or the New menu, the dialog, the provisional file, the JSON, the generation, the
 * file in the editor, and what the notification does afterwards.
 */
internal class EntryToGenerationTest : EntryToGenerationTestBase() {
    // covers: 論点8
    // covers: 論点16
    fun `test 通知の作成するからダイアログを経て仮のファイル JSON 生成と進み開いたファイルの通知は消えて戻らない`() {
        notifyOnEveryFileWithContent()
        val editor = open(writeFile(target, ""))
        val panel = waitForPanel(editor)
        val gate = CompletableDeferred<Unit>()
        val plan = writes(gate = gate)
        gradle.plans += plan
        answerWith()

        panel.link("notification.editor.create").doClick()

        val opened = dialogs.single()
        assertEquals(EntryOrigin.EditorFile(target), opened.origin)
        assertEquals("api.Controller", opened.initialTemplate.template)
        assertEquals(mapOf("resource" to "user", "name" to "User"), opened.seeds)
        waitUntil("the build waits") { plan.reachedGate.isCompleted }
        val provisional = Files.readString(target)
        assertTrue(provisional, provisional.contains("--arg template=api.Controller --arg onExisting=overwrite"))
        assertEquals(provisional, documentText(target))
        assertTrue(service.ledger.entryOf(target) is LedgerEntry.Generating)

        gate.complete(Unit)
        assertEquals(LedgerEntry.Succeeded(opened.initialTemplate), awaitLedger(target))
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        assertEquals(generated, documentText(target))
        assertEquals(listOf(target), openFiles())
        assertEquals(listOf("json", "json", "generate"), gradleKinds())
        // Generated: the same as closing it with the cross (decision 8), however the settings are.
        waitUntil("the notification goes") { panelOf(editor) == null }
        updateAllNotifications()
        assertNull(panelOf(editor))
        assertEmpty(balloons)
    }

    // covers: 論点0
    // covers: 論点1
    fun `test New katachi のテンプレートからダイアログを経てまだ無いディレクトリにファイルが生成されて開く`() {
        val group = KatachiNewGroup()
        val directory = directoryOf(controllerDir.resolve("user"))
        gradle.plans += writes()
        answerWith("name" to "User")
        val event = menuEventOn(group, directory)

        group.update(event)
        waitForIndex("ready") { it is PlacementIndexState.Ready }
        settle()
        group.update(event)
        val item = group.getChildren(event).flattened().filterIsInstance<TemplateItemAction>().single()
        item.actionPerformed(event)

        assertEquals(EntryOrigin.NewMenuDirectory(controllerDir.resolve("user")), dialogs.single().origin)
        assertEquals(mapOf("resource" to "user"), dialogs.single().seeds)
        assertEquals(LedgerEntry.Succeeded(dialogs.single().initialTemplate), awaitLedger(target))
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        assertEquals(generated, Files.readString(target))
        assertEquals(listOf(target), openFiles())
        assertEquals(listOf("json", "json", "generate"), gradleKinds())
        assertEmpty(balloons)
    }

    // covers: 論点12
    // covers: 論点18
    fun `test ダイアログを開いている間に外から書かれたら上書きせず Gradle も走らず理由を出す`() {
        val editor = open(writeFile(target, ""))
        val panel = waitForPanel(editor)
        // Another program writes while the dialog is open; the IDE's file watcher then refreshes the VFS and the open Document.
        // TODO: before that refresh (a moment), an open Document still says "empty" and the provisional file would be written over it.
        answerWith(beforeAnswer = { writeExternally(target, "class Written\n") })

        panel.link("notification.editor.create").doClick()

        waitUntil("the refusal is told") { balloons.isNotEmpty() }
        assertEquals(listOf(entryRefusalText(EntryGenerationRefusal.TargetHasContent(target))), balloonTexts())
        assertEquals("class Written\n", Files.readString(target))
        assertTrue(gradle.generationRequests.isEmpty())
        assertNull(service.ledger.entryOf(target))
    }

    // covers: 論点2
    // covers: 論点17
    fun `test sample-android のモジュールの capture のテンプレートを通知から生成できる`() {
        notifyOnEveryFileWithContent()
        gradle.fixtureOf["arch-a"] = ANDROID_FIXTURE
        val component = archDir.resolve("feature/home/src/main/kotlin/com/example/sample/feature/home/component/HomeCard.kt")
        // katachi does not create modules: the module capture's directory has its build script.
        writeFile(archDir.resolve("feature/home/build.gradle.kts"), "")
        gradle.plans += PlannedGeneration { call -> mapOf(component to "package generated\n\n// ${call.arg("feature")} ${call.arg("name")}\n") }
        val editor = open(writeFile(component, ""))
        val panel = waitForPanel(editor)
        answerWith()

        panel.link("notification.editor.create").doClick()

        val opened = dialogs.single()
        assertEquals("feature.FeatureComponent", opened.initialTemplate.template)
        assertEquals(mapOf("feature" to "home", "name" to "Card"), opened.seeds)
        assertEquals(LedgerEntry.Succeeded(opened.initialTemplate), awaitLedger(component))
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        assertEquals(listOf("--arg template=feature.FeatureComponent", "feature=home", "name=Card"), argsOfGeneration())
        assertEquals("package generated\n\n// home Card\n", documentText(component))
        waitUntil("the notification goes") { panelOf(editor) == null }
        assertEmpty(balloons)
    }

    // covers: 論点12
    fun `test 開いている空のファイルに未保存で入力した後に作成するを押しても何も書かず Gradle も走らない`() {
        val editor = open(writeFile(target, ""))
        val panel = waitForPanel(editor)
        typeInto(editor, "class A")
        answerWith()

        panel.link("notification.editor.create").doClick()

        waitUntil("the refusal is told") { balloons.isNotEmpty() }
        assertEquals(listOf(entryRefusalText(EntryGenerationRefusal.TargetHasContent(target))), balloonTexts())
        assertEquals("", Files.readString(target))
        assertEquals("class A", documentText(target))
        assertTrue(isUnsaved(target))
        assertTrue(gradle.generationRequests.isEmpty())
    }

    // covers: 論点11
    // covers: 論点16
    fun `test properties で失敗した後は通知の作成するからやり直せて成功すると通知が消える`() {
        notifyOnEveryFileWithContent()
        val json = ContractFixtures.json(FIXTURE).replace("Controller.kt", "Controller.properties")
        repeat(6) { gradle.loads += LoadAnswer(json = json) }
        val properties = controllerDir.resolve("user/UserController.properties")
        val editor = open(writeFile(properties, ""))
        gradle.plans += PlannedGeneration(fails = true)
        gradle.plans += PlannedGeneration { mapOf(properties to "name=User\n") }
        answerWith()

        waitForPanel(editor).link("notification.editor.create").doClick()

        assertTrue(awaitLedger(properties) is LedgerEntry.Failed)
        assertEquals(1, balloonTexts().size)
        assertTrue(Files.readString(properties).lines().filter { it.isNotEmpty() }.all { it.startsWith("# ") })
        // The provisional content is empty by the rule: the way in for a retry stays (decision 11).
        waitForPanel(editor).link("notification.editor.create").doClick()

        waitUntil("the retry ends well") { service.ledger.entryOf(properties) is LedgerEntry.Succeeded }
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        assertEquals(2, dialogs.size)
        assertEquals("name=User\n", documentText(properties))
        assertEquals("name=User\n", Files.readString(properties))
        waitUntil("the notification goes") { panelOf(editor) == null }
    }

    /** The `--arg`s of the only generation, as `template`'s spelling then `name=value`s. */
    private fun argsOfGeneration(): List<String> =
        gradle.generationRequests.single().tasks.single().args.map { (name, value) -> if (name == "template") "--arg template=$value" else "$name=$value" }
            .filterNot { it.startsWith("onExisting") }

    private fun writeExternally(path: Path, text: String) {
        Files.writeString(path, text)
        LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path)?.refresh(false, false)
    }

    private fun typeInto(editor: FileEditor, text: String) {
        val document = (editor as TextEditor).editor.document
        WriteCommandAction.runWriteCommandAction(project) { document.insertString(document.textLength, text) }
    }

    companion object {
        const val ANDROID_FIXTURE: String = "sample-android-with-captures"
    }
}
