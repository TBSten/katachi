package me.tbsten.katachi.intellij.data.placement

import java.io.IOException
import java.nio.file.Path

internal fun interface DirectoryLister {
    @Throws(IOException::class)
    fun childDirectories(directory: Path): List<String>
}
