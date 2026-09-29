package me.tbsten.katachi.intellij.ide.dialog

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import me.tbsten.katachi.intellij.data.NioProjectFileSystem
import me.tbsten.katachi.intellij.data.placement.PlacementIndexState
import me.tbsten.katachi.intellij.data.placement.placementRootOf
import me.tbsten.katachi.intellij.ide.KatachiProjectService
import me.tbsten.katachi.intellij.model.templatesOf
import me.tbsten.katachi.intellij.presentation.dialog.CaptureSeedPort
import me.tbsten.katachi.intellij.presentation.entry.GenerateDialogRequest

/**
 * The dialog's inputs from the project: the shared list (followed while the dialog is open), the
 * captures of the current placement index, the generation's existing-file check and the VFS.
 * Call inside `sdkCall`: it creates the project service.
 */
internal fun realGenerateDialogEnvironmentOf(project: Project, request: GenerateDialogRequest): GenerateDialogEnvironment {
    val service = KatachiProjectService.getInstance(project)
    return GenerateDialogEnvironment(
        request = request,
        candidates = templatesOf(service.viewModel.state.value.snapshots),
        seeds = CaptureSeedPort { origin, template ->
            (service.placementIndex.value as? PlacementIndexState.Ready)?.index?.seedsFor(origin, template).orEmpty()
        },
        // The generation the entries run: it knows the provisional content it wrote (a retry, issue 16).
        checkTarget = service.entryGeneration::checkTarget,
        // TODO(V2 L2): asked on the EDT at every dispatch (it looks up the root markers on disk); find each
        //  definition's root once when the dialog opens.
        rootOf = { placementRootOf(it, NioProjectFileSystem) },
        templateChanges = service.viewModel.state.map { templatesOf(it.snapshots) }.distinctUntilChanged(),
        filesChanged = vfsChanges(),
    )
}

/** Emits on every batch of VFS events; the ViewModel's own pause merges the bursts. */
private fun vfsChanges(): Flow<Unit> = callbackFlow {
    val connection = ApplicationManager.getApplication().messageBus.connect()
    connection.subscribe(
        VirtualFileManager.VFS_CHANGES,
        object : BulkFileListener {
            override fun after(events: List<VFileEvent>) {
                trySend(Unit)
            }
        },
    )
    awaitClose { connection.disconnect() }
}.conflate()
