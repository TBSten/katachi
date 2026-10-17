package me.tbsten.katachi.intellij.ide

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.LocalFileSystem
import kotlinx.coroutines.CancellationException
import me.tbsten.katachi.intellij.data.placement.DirectoryLister
import java.nio.file.Files
import java.nio.file.Path

/**
 * [DirectoryLister] over the disk (`java.nio`: Gradle writes behind the IDE's back, so the VFS may be behind) minus
 * the directories the IDE excludes from [project] (`build/` and the like), which it answers from the index in a read
 * action. A directory the VFS does not know yet cannot be excluded, so it is kept.
 *
 * Call it off the EDT. A cancelled read action ends this with a [CancellationException] (its control-flow
 * exception wrapped), never a logged failure.
 *
 * ```kotlin
 * val names = IdeDirectoryLister(project).childDirectories(root.resolve("feature")) // ["home", "settings"]
 * ```
 */
internal class IdeDirectoryLister(private val project: Project) : DirectoryLister {
    override fun childDirectories(directory: Path): List<String> {
        val names = try {
            Files.newDirectoryStream(directory) { Files.isDirectory(it) }.use { stream -> stream.map { it.fileName.toString() } }
        } catch (failure: java.nio.file.DirectoryIteratorException) {
            // java.nio reports an I/O failure in the middle of the iteration this way; the port's contract is IOException.
            throw failure.cause ?: java.io.IOException("Could not read $directory", failure)
        } catch (_: java.nio.file.NoSuchFileException) {
            return emptyList()
        } catch (_: java.nio.file.NotDirectoryException) {
            return emptyList()
        }
        if (names.isEmpty()) return names
        // sdkCall: a platform that answers differently degrades to "nothing excluded", not to a failure.
        val excluded = try {
            sdkCall("ask the project index which of ${names.size} directories are excluded") {
                ReadAction.nonBlocking<Set<String>> { excludedAmong(directory, names) }.executeSynchronously()
            }.getOrDefault(emptySet())
        } catch (control: Throwable) {
            if (control !is ControlFlowException) throw control
            // The project is closing or the read was cancelled: a coroutine's cancellation, so that the presentation layer
            // (which knows no IntelliJ type) rethrows it instead of logging a failed read.
            throw CancellationException("The read of the excluded directories was cancelled", control)
        }
        return names.filterNot { it in excluded }
    }

    private fun excludedAmong(directory: Path, names: List<String>): Set<String> {
        val index = ProjectFileIndex.getInstance(project)
        val local = LocalFileSystem.getInstance()
        return names.filterTo(HashSet()) { name ->
            val file = local.findFileByNioFile(directory.resolve(name))
            file != null && index.isExcluded(file)
        }
    }
}
