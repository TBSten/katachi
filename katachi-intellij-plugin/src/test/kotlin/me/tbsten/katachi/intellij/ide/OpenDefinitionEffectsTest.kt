package me.tbsten.katachi.intellij.ide

import com.intellij.notification.Notification
import com.intellij.notification.Notifications
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.testFramework.PlatformTestUtil
import kotlinx.coroutines.async
import me.tbsten.katachi.intellij.ide.definition.DeclarationSiteResolver
import me.tbsten.katachi.intellij.model.DeclarationSiteRef
import me.tbsten.katachi.intellij.model.KatachiModule
import java.nio.file.Files
import java.util.Collections

/**
 * ⋯ › Open Definition on real files (design T-10): the IDE opens the Role's declaration found among the definition's test
 * sources, and tells in a balloon when it cannot, with the gutter's sentence.
 */
internal class OpenDefinitionEffectsTest : KatachiIdeTestBase() {
    private val balloons: MutableList<Notification> = Collections.synchronizedList(mutableListOf())
    private lateinit var arch: KatachiModule

    override fun setUp() {
        super.setUp()
        arch = KatachiModule(":arch", moduleDirOf(":arch"), root, "project")
        project.messageBus.connect(testRootDisposable).subscribe(
            Notifications.TOPIC,
            object : Notifications {
                override fun notify(notification: Notification) {
                    if (notification.groupId == KatachiNotifications.GROUP_ID) balloons += notification
                }
            },
        )
    }

    private fun effects() = IdeEffectsImpl(project, siteResolver = { DeclarationSiteResolver(project, scope) })

    private fun open(roleName: String, site: DeclarationSiteRef?) {
        val done = scope.async { effects().openDeclaration(arch, roleName, site) }
        PlatformTestUtil.waitWithEventsDispatching("open the declaration", { done.isCompleted }, 10)
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    }

    private fun writeRole(name: String) {
        val file = arch.directory.resolve("src/test/kotlin/roles/$name")
        Files.createDirectories(file.parent)
        Files.writeString(file, "package roles\n\n// role\nobject Role\n")
    }

    fun `test Role の宣言位置のファイルを開き、その行にカーソルを置く`() {
        writeRole("ServiceRole.kt")

        open("domain.Service", DeclarationSiteRef("ServiceRole.kt", 3))

        val selected = FileEditorManager.getInstance(project).selectedFiles.single()
        assertEquals("ServiceRole.kt", selected.name)
        assertEmpty(balloons)
        val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: throw AssertionError("nothing was opened")
        assertEquals(2, editor.caretModel.logicalPosition.line)
    }

    fun `test 宣言位置が JSON に無い Role は開かず 見つからない旨を Role の名前で知らせる`() {
        open("domain.Service", null)

        assertEmpty(FileEditorManager.getInstance(project).openFiles.toList())
        waitUntilBalloon()
        assertEquals(KatachiBundle.message("gutter.notFound", "domain.Service"), balloons.single().content)
    }

    fun `test 宣言元のファイルが無ければ開かず 見つからない旨を知らせる`() {
        writeRole("OtherRole.kt")

        open("domain.Service", DeclarationSiteRef("ServiceRole.kt", 1))

        assertEmpty(FileEditorManager.getInstance(project).openFiles.toList())
        waitUntilBalloon()
        assertEquals(KatachiBundle.message("gutter.notFound", "domain.Service"), balloons.single().content)
    }

    fun `test 同じ名前のファイルが2つあれば どちらも開かず 見つからない旨を知らせる`() {
        writeRole("ServiceRole.kt")
        Files.createDirectories(arch.directory.resolve("src/test/kotlin/other"))
        Files.writeString(arch.directory.resolve("src/test/kotlin/other/ServiceRole.kt"), "// other\n")

        open("domain.Service", DeclarationSiteRef("ServiceRole.kt", 1))

        assertEmpty(FileEditorManager.getInstance(project).openFiles.toList())
        waitUntilBalloon()
        assertEquals(KatachiBundle.message("gutter.notFound", "domain.Service"), balloons.single().content)
    }

    private fun waitUntilBalloon() =
        PlatformTestUtil.waitWithEventsDispatching("a balloon", { balloons.isNotEmpty() }, 10)
}
