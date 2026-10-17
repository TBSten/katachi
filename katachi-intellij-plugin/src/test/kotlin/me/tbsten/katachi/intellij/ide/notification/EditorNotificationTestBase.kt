package me.tbsten.katachi.intellij.ide.notification

import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.impl.ExtensionPointImpl
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import com.intellij.ui.EditorNotifications
import com.intellij.ui.EditorNotificationsImpl
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.InplaceButton
import com.intellij.util.ThrowableRunnable
import com.intellij.util.ui.UIUtil
import me.tbsten.katachi.intellij.data.NioProjectFileSystem
import me.tbsten.katachi.intellij.data.detect.detectDefinitionModules
import me.tbsten.katachi.intellij.data.gradle.GradleTaskRunner
import me.tbsten.katachi.intellij.data.placement.PlacementIndexState
import me.tbsten.katachi.intellij.ide.EntryServiceTestBase
import me.tbsten.katachi.intellij.ide.IdeEffectsImpl
import me.tbsten.katachi.intellij.ide.KatachiBundle
import me.tbsten.katachi.intellij.ide.KatachiPorts
import me.tbsten.katachi.intellij.ide.KatachiProjectService
import me.tbsten.katachi.intellij.ide.KatachiSettings
import me.tbsten.katachi.intellij.ide.LoadAnswer
import me.tbsten.katachi.intellij.ide.definition.DefinitionModelService
import me.tbsten.katachi.intellij.ide.definition.FakeDefinitionModelService
import me.tbsten.katachi.intellij.ide.rules.KatachiBaselineService
import me.tbsten.katachi.intellij.ide.rules.RulesPorts
import me.tbsten.katachi.intellij.ide.rules.ruleSettingsOf
import me.tbsten.katachi.intellij.presentation.rules.VcsTracking
import me.tbsten.katachi.intellij.model.DetectionResult
import me.tbsten.katachi.intellij.presentation.entry.EntryEffects
import me.tbsten.katachi.intellij.presentation.entry.GenerateDialogRequest
import me.tbsten.katachi.intellij.presentation.entry.PlacementMatch
import me.tbsten.katachi.intellij.testing.FakeEntryEffects
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * Platform tests of the real [KatachiEditorNotificationProvider] over real files in [root]: the
 * extension point holds only it (the bundled Vue provider throws and stops the others, S2 (b)), the
 * project service is created by the provider's own port over the fake synced data and Gradle, the
 * entry effects are recorded in [effects]. Panels are read after the platform's async work with
 * [panelOf], never after a sleep.
 */
internal abstract class EditorNotificationTestBase : EntryServiceTestBase() {
    val effects = FakeEntryEffects()

    /** What the service's entry effects are: [effects], failing once when asked to. */
    val entryEffects = FailOnceEntryEffects(effects)

    /** How many project services the provider asked to create. */
    val servicesCreated = AtomicInteger(0)

    /** Runs first in each settings read: a test makes it throw to play an SDK call that fails. */
    @Volatile var onSettingsRead: () -> Unit = {}

    /** The runner the next created service loads through. */
    var serviceRunner: GradleTaskRunner? = null

    /** What the no-role notification asks the definition: a fake by default, a test may put the real service here. */
    @Volatile var definitions: DefinitionModelService? = FakeDefinitionModelService()

    /** What the VCS says of each file; a test changes it and fires a status change. */
    @Volatile var vcsOf: (VirtualFile) -> VcsTracking = { VcsTracking.Tracked }

    /** What the VCS says once it has settled, `null` before it has; `null` here means the same as [vcsOf] (a VCS that is always settled). */
    @Volatile var vcsSettledOf: ((VirtualFile) -> VcsTracking?)? = null

    val ruleEffects = FakeRuleEffects()

    /** The popups the no-role notification was asked to open, in order. */
    val popups = CopyOnWriteArrayList<ShownPopup>()

    lateinit var provider: KatachiEditorNotificationProvider
    lateinit var noRoleProvider: NoRoleNotificationProvider
    lateinit var memoryService: EditorNotificationMemoryService

    @Volatile private var created: KatachiProjectService? = null
    private val settings get() = KatachiSettings.getInstance(project)
    private val notifications get() = EditorNotifications.getInstance(project) as EditorNotificationsImpl
    private var saved = listOf<Boolean>()
    private var savedNoRole = true

    override fun setUp() {
        super.setUp()
        saved = listOf(settings.editorNotificationEnabled, settings.notifyOnEmptyFile, settings.notifyOnFileWithContent, settings.notifyOnContentOnce)
        savedNoRole = settings.notifyOnNoRole
        memoryService = newMemoryService()
        provider = KatachiEditorNotificationProvider({ ports() }, { rulesPorts() })
        noRoleProvider = NoRoleNotificationProvider({ rulesPorts() }, { project, file, path -> provider.templateDecisionOf(project, file, path) }) { under, group ->
            popups += ShownPopup(under, group)
        }
        @Suppress("UNCHECKED_CAST")
        (EditorNotificationProvider.EP_NAME.getPoint(project) as ExtensionPointImpl<EditorNotificationProvider>).maskAll(listOf(provider, noRoleProvider), testRootDisposable, true)
    }

    override fun tearDown() {
        try {
            settings.editorNotificationEnabled = saved[0]
            settings.notifyOnEmptyFile = saved[1]
            settings.notifyOnFileWithContent = saved[2]
            settings.notifyOnContentOnce = saved[3]
            settings.notifyOnNoRole = savedNoRole
        } finally {
            super.tearDown()
        }
    }

    // Opening an editor starts the bundled Vue LSP support, which logs an error in this IDE build (S2 (b)).
    override fun runTestRunnable(testRunnable: ThrowableRunnable<Throwable>) {
        ignoreUnrelatedLoggedErrors { super.runTestRunnable(testRunnable) }
    }

    open fun ports(): NotificationPorts = NotificationPorts(
        existingService = { created },
        hasDefinitionModule = { detectDefinitionModules(synced).let { it is DetectionResult.Found || it is DetectionResult.TaskListMissing } },
        service = { created ?: createService() },
        settings = {
            onSettingsRead()
            entrySettingsOf(settings)
        },
        memory = { memoryService },
    )

    fun rulesPorts(): RulesPorts = RulesPorts(
        definitions = { definitions },
        settings = { ruleSettingsOf(settings) },
        memory = { memoryService },
        vcs = { vcsOf(it) },
        vcsIfSettled = { file -> vcsSettledOf.let { settled -> if (settled == null) vcsOf(file) else settled(file) } },
        effects = { ruleEffects },
        // The baseline lives in the test's real directory, not under the fake definition's root.
        baseline = { _, baseline -> project.service<KatachiBaselineService>().reading(root.resolve(baseline)) },
    )

    @Synchronized
    private fun createService(): KatachiProjectService {
        created?.let { return it }
        servicesCreated.incrementAndGet()
        val ports = KatachiPorts(
            syncedProject = { synced },
            runner = serviceRunner ?: gradle,
            fileSystem = NioProjectFileSystem,
            effects = IdeEffectsImpl(project),
            entryEffects = entryEffects,
            importInProgress = { importing },
        )
        return KatachiProjectService(project, scope, ports).also {
            Disposer.register(testRootDisposable, it)
            service = it
            created = it
        }
    }

    /** A new memory, as a reopened project has; the old one stops listening. */
    fun recreateMemoryService() {
        Disposer.dispose(memoryService)
        memoryService = newMemoryService()
    }

    private fun newMemoryService() = EditorNotificationMemoryService(project, scope).also { Disposer.register(testRootDisposable, it) }

    /** Loads [json] into every definition of the synced data through the provider's service and waits for the index. */
    fun loadIndex(json: String = NOTIFICATION_JSON): PlacementIndexState.Ready {
        gradle.loads += LoadAnswer(json)
        ports().service().ensureLoaded()
        val ready = waitForIndex("ready") { it is PlacementIndexState.Ready } as PlacementIndexState.Ready
        settle()
        return ready
    }

    /** The matches the loaded index has for [relative], in index order. */
    fun matchesOf(relative: String): List<PlacementMatch> =
        (service.placementIndex.value as PlacementIndexState.Ready).index.matchesForFile(root.resolve(relative))

    /**
     * The text of the template notification ([kind] is `empty` or `matched`) that [relative] should get, written out from the
     * matches: the title of the one template that fits (its role's name when it has none), or the count of several.
     */
    fun noticeTextOf(kind: String, relative: String): String {
        val matches = matchesOf(relative)
        val only = matches.singleOrNull()?.template?.template
        return if (only != null) {
            KatachiBundle.message("notification.editor.$kind.one", only.title.ifBlank { only.roleName })
        } else {
            KatachiBundle.message("notification.editor.$kind.many", matches.size)
        }
    }

    fun write(relative: String, text: String): VirtualFile {
        val path = root.resolve(relative)
        Files.createDirectories(path.parent)
        Files.writeString(path, text)
        return LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path) ?: throw AssertionError("Not in the VFS: $path")
    }

    /** Opens [file] in an editor, loaded, as the platform needs for notifications. */
    fun open(file: VirtualFile): FileEditor {
        val editor = FileEditorManager.getInstance(project).openFile(file, true).single()
        EditorTestUtil.waitForLoading((editor as TextEditor).editor)
        return editor
    }

    fun open(relative: String, text: String): FileEditor = open(write(relative, text))

    /** The editor [file] is open in. */
    fun editorOf(file: VirtualFile): FileEditor =
        FileEditorManager.getInstance(project).getEditors(file).firstOrNull() ?: throw AssertionError("Not open: ${file.path}")

    fun close(file: VirtualFile) = FileEditorManager.getInstance(project).closeFile(file)

    fun reopen(file: VirtualFile): FileEditor {
        close(file)
        return open(file)
    }

    /** Lets the platform finish its pending notification updates: they start on the EDT, then run in the background. */
    fun settleNotifications() {
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        notifications.completeAsyncTasks()
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    }

    /** katachi's panel on [editor] once the pending updates are done; `null` when there is none. */
    fun panelOf(editor: FileEditor): EditorNotificationPanel? {
        settleNotifications()
        return notifications.getNotificationPanels(editor).entries.firstOrNull { it.key == KatachiEditorNotificationProvider::class.java }?.value as EditorNotificationPanel?
    }

    /** The panel of the no-role notification on [editor] once the pending updates are done; `null` when there is none. */
    fun noRolePanelOf(editor: FileEditor): EditorNotificationPanel? {
        settleNotifications()
        return notifications.getNotificationPanels(editor).entries.firstOrNull { it.key == NoRoleNotificationProvider::class.java }?.value as EditorNotificationPanel?
    }

    /**
     * The no-role panel on [editor] as it is now, without waiting for the platform's pending work: the platform's wait
     * (`completeAsyncTasks`) fails when an update starts while it waits, as the delayed redraws after a definition change do.
     * Poll this under `waitWithEventsDispatching`; ask [noRolePanelOf] only when nothing is on its way.
     */
    fun peekNoRolePanel(editor: FileEditor): EditorNotificationPanel? =
        notifications.getNotificationPanels(editor).entries.firstOrNull { it.key == NoRoleNotificationProvider::class.java }?.value as EditorNotificationPanel?

    fun updateAll() {
        notifications.updateAllNotifications()
        settleNotifications()
    }

    fun path(relative: String): Path = root.resolve(relative)
}

/** The links of the panel ([Create] or [View Template]); ⚙ and × are buttons, not links. */
internal val EditorNotificationPanel.links: List<HyperlinkLabel> get() = UIUtil.findComponentsOfType(this, HyperlinkLabel::class.java)

internal fun EditorNotificationPanel.link(key: String): HyperlinkLabel =
    findLabelByName(KatachiBundle.message(key)) ?: throw AssertionError("No link ${KatachiBundle.message(key)} in $text")

/**
 * ⚙ or ×: an [InplaceButton] found by its tooltip ([key] of KatachiBundle). `doClick()` presses it
 * as a mouse click would (contract.md: how tests press the notification's buttons).
 */
internal fun EditorNotificationPanel.button(key: String): InplaceButton {
    val tooltip = KatachiBundle.message(key)
    return UIUtil.findComponentsOfType(this, InplaceButton::class.java).singleOrNull { it.toolTipText == tooltip }
        ?: throw AssertionError("No button '$tooltip' in $text")
}

/** [fake], except that the next dialog to open throws when [failNext] is set, as a failing SDK call would. */
internal class FailOnceEntryEffects(private val fake: FakeEntryEffects) : EntryEffects by fake {
    @Volatile var failNext: Boolean = false

    override fun openGenerateDialog(request: GenerateDialogRequest) {
        if (failNext) {
            failNext = false
            throw IllegalStateException("The generate dialog could not open (a test failure)")
        }
        fake.openGenerateDialog(request)
    }
}
