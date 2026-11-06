package me.tbsten.katachi.intellij.data.script

import java.nio.file.InvalidPathException
import java.nio.file.Path
import kotlin.io.path.Path

/**
 * Where `katachi` may be. PATH first; then the directories it is installed to, because an IDE started from the
 * Dock or a desktop entry does not get the PATH of the login shell.
 */
internal class KatachiCliLocator(
    private val pathVariable: String?,
    private val home: String?,
    private val windows: Boolean,
    private val isExecutable: (Path) -> Boolean,
) {
    fun candidates(): List<Path> {
        val separator = if (windows) ';' else ':'
        val fromPath = pathVariable.orEmpty().split(separator).filter { it.isNotBlank() }
        val installed = if (windows) emptyList() else listOfNotNull("/opt/homebrew/bin", "/usr/local/bin", home?.let { "$it/.local/bin" })
        val names = if (windows) listOf("katachi.bat", "katachi.cmd", "katachi.exe") else listOf("katachi")
        return (fromPath + installed).distinct().flatMap { dir -> names.mapNotNull { name -> pathOrNull(dir)?.resolve(name) } }
    }

    fun locate(): Path? = candidates().firstOrNull(isExecutable)

    private fun pathOrNull(text: String): Path? = try {
        Path(text)
    } catch (_: InvalidPathException) {
        null
    }
}
