package me.tbsten.katachi.intellij

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.sdk.Project

private const val ENTRY_PLUGIN = "me.tbsten.katachi.intellij"

/** Read-only bridge: no generation, field filling, or reveal actions are invoked remotely. */
@Remote("me.tbsten.katachi.intellij.ide.debug.KatachiDebugBridge", plugin = ENTRY_PLUGIN)
interface EditorEntryBridgeRef {
    fun describeState(): String
    fun describeDialog(): String
}

/**
 * The shipped debug bridge does not yet print view.highlight. Enrich its describeState on the
 * test side with the real ViewModel value, using getters only, without changing production code.
 * Resolve the project service lazily: scenario 4 must reach it first through the notification.
 */
internal class EditorEntryProbe(private val driver: Driver, private val project: Project) {
    private val bridge by lazy { driver.service(EditorEntryBridgeRef::class, project) }

    fun describeDialog(): String = bridge.describeDialog()

    fun describeState(): String {
        val description = bridge.describeState()
        val highlighted = driver.service(EntryProjectServiceRef::class, project)
            .getViewModel().getState().getValue().getView().getHighlight()?.getTemplateId()?.getTemplate()
        return "$description\nhighlighted=${highlighted.orEmpty()}"
    }
}

@Remote("me.tbsten.katachi.intellij.ide.KatachiProjectService", plugin = ENTRY_PLUGIN)
interface EntryProjectServiceRef {
    fun getViewModel(): EntryViewModelRef
}

@Remote("me.tbsten.katachi.intellij.presentation.KatachiToolWindowViewModel", plugin = ENTRY_PLUGIN)
interface EntryViewModelRef {
    fun getState(): EntryStateFlowRef
}

@Remote("kotlinx.coroutines.flow.StateFlow", plugin = ENTRY_PLUGIN)
interface EntryStateFlowRef {
    fun getValue(): EntryScreenStateRef
}

@Remote("me.tbsten.katachi.intellij.presentation.KatachiScreenState", plugin = ENTRY_PLUGIN)
interface EntryScreenStateRef {
    fun getView(): EntryViewStateRef
}

@Remote("me.tbsten.katachi.intellij.presentation.ViewState", plugin = ENTRY_PLUGIN)
interface EntryViewStateRef {
    fun getHighlight(): EntryHighlightRef?
}

@Remote("me.tbsten.katachi.intellij.presentation.Highlight", plugin = ENTRY_PLUGIN)
interface EntryHighlightRef {
    fun getTemplateId(): EntryTemplateIdRef
}

@Remote("me.tbsten.katachi.intellij.model.TemplateId", plugin = ENTRY_PLUGIN)
interface EntryTemplateIdRef {
    fun getTemplate(): String
}

/** Read existing VFS children without refreshing from disk, which could hide ghost entries. */
internal fun Driver.entryVfsSnapshot(directory: String): Set<String> {
    val root = utility(EntryLocalFileSystemRef::class).getInstance().findFileByPath(directory)
        ?: error("Directory missing from VFS: $directory")
    fun descendants(file: EntryVirtualFileRef): Set<String> = buildSet {
        add(file.getPath())
        if (file.isDirectory()) file.getChildren().forEach { addAll(descendants(it)) }
    }
    return descendants(root)
}

@Remote("com.intellij.openapi.vfs.LocalFileSystem")
interface EntryLocalFileSystemRef {
    fun getInstance(): EntryLocalFileSystemRef
    fun findFileByPath(path: String): EntryVirtualFileRef?
}

@Remote("com.intellij.openapi.vfs.VirtualFile")
interface EntryVirtualFileRef {
    fun getPath(): String
    fun isDirectory(): Boolean
    fun getChildren(): Array<EntryVirtualFileRef>
}
