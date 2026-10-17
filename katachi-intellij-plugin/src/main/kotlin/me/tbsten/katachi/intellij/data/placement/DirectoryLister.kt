package me.tbsten.katachi.intellij.data.placement

import java.io.IOException
import java.nio.file.Path

/**
 * The directories that exist under a directory: the port [CaptureCandidates] reads the project tree through.
 * The real one is `IdeDirectoryLister` (the disk, minus what the IDE excludes); tests put a tree in memory.
 *
 * ```kotlin
 * val names = lister.childDirectories(root.resolve("feature")) // ["home", "settings"]
 * ```
 */
internal fun interface DirectoryLister {
    /**
     * The names (not paths) of the directories directly under [directory], in no particular order. Empty when
     * [directory] does not exist or has none. Directories the IDE excludes (`build/` and the like) are left out here;
     * names starting with `.` are left out by the caller.
     *
     * @throws IOException when the directory cannot be read.
     */
    @Throws(IOException::class)
    fun childDirectories(directory: Path): List<String>
}
