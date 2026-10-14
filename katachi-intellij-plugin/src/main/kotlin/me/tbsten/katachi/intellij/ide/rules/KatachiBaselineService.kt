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

/**
 * Reads the `katachi-baseline.json` files the "no role holds this file" notification asks about, and remembers them.
 *
 * [reading] is for the background thread (it reads the disk). A file is read again when it changed on disk since the
 * last read (its modification time and size), and [onBaselineChanged], called by the file watcher, drops what was read
 * and redraws every notification. The paths that were asked about are the ones the watcher follows ([watchedFiles]).
 * A `"version"` the plugin does not know is logged at WARN once per read, and treated as unreadable.
 *
 * ```kotlin
 * val reading = project.service<KatachiBaselineService>().reading(projectRoot.resolve("katachi-baseline.json"))
 * ```
 */
@Service(Service.Level.PROJECT)
internal class KatachiBaselineService(private val project: Project) {
    private class Remembered(val stamp: String, val reading: BaselineReading)

    private val remembered = ConcurrentHashMap<Path, Remembered>()

    /** The baseline files read so far, absolute and normalized: what the file watcher compares VFS events with. */
    val watchedFiles: Set<Path> get() = remembered.keys.toSet()

    fun reading(file: Path): BaselineReading {
        val key = file.toAbsolutePath().normalize()
        val stamp = stampOf(key)
        remembered[key]?.takeIf { it.stamp == stamp }?.let { return it.reading }
        val reading = readBaselineFile(key)
        warnIfUnusable(key, reading)
        remembered[key] = Remembered(stamp, reading)
        return reading
    }

    /** A baseline file changed: forget what was read and draw the notifications again. */
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
