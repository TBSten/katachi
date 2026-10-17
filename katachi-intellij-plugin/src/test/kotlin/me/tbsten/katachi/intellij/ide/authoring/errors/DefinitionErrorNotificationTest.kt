package me.tbsten.katachi.intellij.ide.authoring.errors

import com.intellij.openapi.extensions.impl.ExtensionPointImpl
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import com.intellij.ui.EditorNotifications
import com.intellij.ui.EditorNotificationsImpl
import com.intellij.ui.HyperlinkLabel
import com.intellij.util.ui.UIUtil
import me.tbsten.katachi.intellij.ide.KatachiBundle
import me.tbsten.katachi.intellij.ide.definition.RefreshReason
import me.tbsten.katachi.intellij.model.DeclarationSiteRef
import me.tbsten.katachi.intellij.model.DefinitionErrorsEvent
import me.tbsten.katachi.intellij.model.DefinitionState
import me.tbsten.katachi.intellij.model.Freshness
import me.tbsten.katachi.intellij.model.StaleReason
import me.tbsten.katachi.intellij.testing.AnnotationFixtures

/** F-9 of ide-authoring: the banner "katachi could not read the definition" in the test sources of the definition module. */
internal class DefinitionErrorNotificationTest : DefinitionErrorTestBase() {
    private lateinit var banner: DefinitionErrorNotificationProvider
    private val details: MutableList<String> = java.util.Collections.synchronizedList(mutableListOf())

    override fun setUp() {
        super.setUp()
        banner = DefinitionErrorNotificationProvider({ ports() }) { _, text -> details += text }
    }

    private fun editorOf(file: VirtualFile): FileEditor {
        val editor = FileEditorManager.getInstance(project).openFile(file, true).single()
        EditorTestUtil.waitForLoading((editor as TextEditor).editor)
        return editor
    }

    /** A second test source of the definition module, open in an editor. */
    private fun otherSource(): Pair<VirtualFile, FileEditor> {
        val file = myFixture.addFileToProject("Other.kt", "// other\n").virtualFile
        definitions.holdDefinitionFile(file, AnnotationFixtures.MODULE)
        return file to editorOf(file)
    }

    private fun panelOf(file: VirtualFile, editor: FileEditor): EditorNotificationPanel? =
        banner.collectNotificationData(project, file)?.apply(editor) as EditorNotificationPanel?

    private val EditorNotificationPanel.links: List<HyperlinkLabel> get() = UIUtil.findComponentsOfType(this, HyperlinkLabel::class.java)

    private fun EditorNotificationPanel.link(key: String): HyperlinkLabel =
        links.firstOrNull { it.text == KatachiBundle.message(key) } ?: throw AssertionError("No link ${KatachiBundle.message(key)} among ${links.map { it.text }}")

    fun `test 誤りがあると 定義モジュールの test ソースの通知に 最初の誤りの 1 行目と 3 つのリンクが出る`() {
        openDefinition()
        rejected(mistake(4, "first line\nsecond line"), mistake(null, "other"))
        val (file, editor) = otherSource()

        val panel = panelOf(file, editor) ?: throw AssertionError("no panel")

        assertEquals(KatachiBundle.message("authoring.error.notification", "first line") + KatachiBundle.message("authoring.error.notification.more", 1), panel.text)
        assertEquals(
            listOf("authoring.error.notification.goTo", "authoring.error.notification.reload", "authoring.error.notification.details").map { KatachiBundle.message(it) },
            panel.links.map { it.text },
        )
    }

    fun `test 場所の分かる誤りが無ければ 誤りの場所へ は出ず 読み直しと詳細だけ`() {
        openDefinition()
        rejected(mistake(null))
        val (file, editor) = otherSource()

        val panel = panelOf(file, editor) ?: throw AssertionError("no panel")

        assertEquals(listOf("authoring.error.notification.reload", "authoring.error.notification.details").map { KatachiBundle.message(it) }, panel.links.map { it.text })
    }

    fun `test 変更の後の通知には 変更の前の結果 と付く`() {
        openDefinition()
        rejected(mistake(4))
        definitions.emitErrors(DefinitionErrorsEvent.DefinitionChanged)
        val (file, editor) = otherSource()

        val panel = panelOf(file, editor) ?: throw AssertionError("no panel")

        assertTrue(panel.text, panel.text.endsWith(KatachiBundle.message("authoring.error.notification.stale")))
    }

    fun `test 誤りの場所へ を押すと 誤りのファイルのその行が開く`() {
        openDefinition()
        rejected(mistake(4))
        val (file, editor) = otherSource()
        val panel = panelOf(file, editor) ?: throw AssertionError("no panel")

        panel.link("authoring.error.notification.goTo").doClick()

        val manager = FileEditorManager.getInstance(project)
        assertEquals("ScreenRole.kt", manager.selectedFiles.single().name)
        assertEquals(3, manager.selectedTextEditor!!.caretModel.logicalPosition.line)
    }

    fun `test 最初の誤りの場所が見つからなくても 見つかる次の誤りの行へ飛べる`() {
        openDefinition()
        val lost = mistake(null).copy(site = DeclarationSiteRef("Missing.kt", 2))
        rejected(lost, mistake(4))
        val (file, editor) = otherSource()

        val panel = panelOf(file, editor) ?: throw AssertionError("no panel")
        panel.link("authoring.error.notification.goTo").doClick()

        val manager = FileEditorManager.getInstance(project)
        assertEquals("ScreenRole.kt", manager.selectedFiles.single().name)
        assertEquals(3, manager.selectedTextEditor!!.caretModel.logicalPosition.line)
    }

    fun `test 定義を読み直す を押すと 読み直しの依頼が利用者の操作として出る`() {
        openDefinition()
        rejected(mistake(4))
        val (file, editor) = otherSource()

        (panelOf(file, editor) ?: throw AssertionError("no panel")).link("authoring.error.notification.reload").doClick()

        assertEquals(listOf(RefreshReason.UserAction), definitions.refreshes)
    }

    fun `test 詳細を表示 を押すと 場所と全文が誤りの数だけ出る`() {
        openDefinition()
        rejected(mistake(4, "first\nbody"), mistake(null, "second"))
        val (file, editor) = otherSource()

        (panelOf(file, editor) ?: throw AssertionError("no panel")).link("authoring.error.notification.details").doClick()

        assertEquals(listOf("ScreenRole.kt:4\nfirst\nbody\n\nsecond"), details)
    }

    fun `test 定義モジュールの test ソースでないファイルと 誤りの無い間は 通知が出ない`() {
        openDefinition()
        val stranger = myFixture.addFileToProject("Stranger.kt", "// stranger\n").virtualFile
        val strangerEditor = editorOf(stranger)
        val (file, editor) = otherSource()

        rejected(mistake(4))
        assertNull("not a test source of the definition module", panelOf(stranger, strangerEditor))
        assertNotNull(panelOf(file, editor))

        definitions.emitErrors(DefinitionErrorsEvent.LoadOk(tick()))
        assertNull(panelOf(file, editor))
    }

    fun `test 定義モジュールが無いプロジェクトでは 通知は出ない`() {
        openDefinition()
        rejected(mistake(4))
        val (file, editor) = otherSource()
        hasDefinitionModule = false

        assertNull(panelOf(file, editor))
    }

    fun `test プラットフォームの通知の仕組みでは 誤りが出ると出て 読み込みが成功すると 描き直しの依頼で消える`() {
        @Suppress("UNCHECKED_CAST")
        (EditorNotificationProvider.EP_NAME.getPoint(project) as ExtensionPointImpl<EditorNotificationProvider>).maskAll(listOf(banner), testRootDisposable, true)
        val notifications = EditorNotifications.getInstance(project) as EditorNotificationsImpl
        fun shown(editor: FileEditor): Boolean {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
            notifications.completeAsyncTasks()
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
            return notifications.getNotificationPanels(editor).keys.any { it == DefinitionErrorNotificationProvider::class.java }
        }
        openDefinition()
        val (_, editor) = otherSource()
        rejected(mistake(4))
        refresherWatch.reset()
        notifications.updateAllNotifications()
        assertTrue("the banner is shown once the mistake is known", shown(editor))

        definitions.emitErrors(DefinitionErrorsEvent.LoadOk(tick()))
        refresherWatch.settle()

        assertFalse("the refresher asked the platform to compute the banner again", shown(editor))
    }

    /** The platform's own notification machinery over [banner], with a rejected definition shown in [otherSource]'s editor. */
    private fun bannerShownAfter(change: () -> Unit): Boolean {
        @Suppress("UNCHECKED_CAST")
        (EditorNotificationProvider.EP_NAME.getPoint(project) as ExtensionPointImpl<EditorNotificationProvider>).maskAll(listOf(banner), testRootDisposable, true)
        val notifications = EditorNotifications.getInstance(project) as EditorNotificationsImpl
        openDefinition()
        val (_, editor) = otherSource()
        rejected(mistake(4))
        refresherWatch.reset()
        notifications.updateAllNotifications()
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        notifications.completeAsyncTasks()
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        assertTrue("the banner is shown once the mistake is known", notifications.getNotificationPanels(editor).keys.any { it == DefinitionErrorNotificationProvider::class.java })

        change()
        refresherWatch.settle()
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        notifications.completeAsyncTasks()
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        return notifications.getNotificationPanels(editor).keys.any { it == DefinitionErrorNotificationProvider::class.java }
    }

    fun `test 定義の状態と誤りが同じ瞬間に変わっても 描き直しの理由に関わらず 帯は消える`() {
        val shown = bannerShownAfter {
            definitions.emit(DefinitionState.Ready(listOf(snapshot), Freshness.Stale(tick(), StaleReason.DefinitionChanged)))
            definitions.emitErrors(DefinitionErrorsEvent.LoadOk(tick()))
        }
        assertFalse("the banner is gone once the load read the definition", shown)
    }

    fun `test 誤りが消えた直後に 定義の状態だけが続けて変わっても 帯は消える`() {
        val shown = bannerShownAfter {
            definitions.emitErrors(DefinitionErrorsEvent.LoadOk(tick()))
            definitions.emit(DefinitionState.Ready(listOf(snapshot), Freshness.Stale(tick(), StaleReason.DefinitionChanged)))
        }
        assertFalse("a later change of the state does not hide that the mistakes went", shown)
    }
}
