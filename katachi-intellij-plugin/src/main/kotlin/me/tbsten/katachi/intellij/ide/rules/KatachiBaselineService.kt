package me.tbsten.katachi.intellij.ide.rules

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.ui.EditorNotifications
import me.tbsten.katachi.intellij.data.baseline.BaselineReading
import me.tbsten.katachi.intellij.data.baseline.KNOWN_BASELINE_VERSION
import me.tbsten.katachi.intellij.data.baseline.readBaselineFile
import me.tbsten.katachi.intellij.ide.sdkCall
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

// `reading` is for the background thread (it reads the disk).
@Service(Service.Level.PROJECT)
internal class KatachiBaselineService(private val project: Project) {
    private class Remembered(val stamp: String, val reading: BaselineReading)

    private val remembered = ConcurrentHashMap<Path, Remembered>()

    val watchedFiles: Set<Path> get() = remembered.keys.toSet()

    fun reading(file: Path): BaselineReading {
        val key = file.toAbsolutePath().normalize()
        val stamp = stampOf(key)
        // compute is atomic per key: two threads asking at once read once, so the WARN is logged once per read.
        return remembered.compute(key) { _, current ->
            if (current != null && current.stamp == stamp) {
                current
            } else {
                val reading = readBaselineFile(key)
                warnIfUnusable(key, reading)
                Remembered(stamp, reading)
            }
        }!!.reading
    }

    fun onBaselineChanged() {
        val files = remembered.keys.toList()
        remembered.clear()
        // Read again by the next decision, which keeps watching the same files.
        if (files.isEmpty()) return
        sdkCall("update the notifications after the baseline changed") { EditorNotifications.getInstance(project).updateAllNotifications() }
    }

    private fun stampOf(file: Path): String = try {
        "${Files.getLastModifiedTime(file).toMillis()}/${Files.size(file)}"
    } catch (_: java.io.IOException) {
        "none"
    }

    private fun warnIfUnusable(file: Path, reading: BaselineReading) {
        if (reading is BaselineReading.UnknownVersion) {
            LOG.warn("Not using $file: its \"version\" is ${reading.version ?: "not a whole number"}, and this plugin reads version $KNOWN_BASELINE_VERSION only.")
        }
    }

    private companion object {
        val LOG: Logger = Logger.getInstance(KatachiBaselineService::class.java)
    }
}
