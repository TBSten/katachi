package me.tbsten.katachi.konsist.internal

import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import java.lang.ref.SoftReference
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

// `ctime` and the inode are keyed too because a tool cannot set them back: `cp -p` and `touch -r` restore an mtime, but a write moves ctime and a rename changes the inode.
internal data class FileStamp(
    val size: Long,
    val modifiedNanos: Long,
    val changedNanos: Long,
    val fileKey: Any?,
)

// Never return a stale parse: store only under a stamp taken before and unchanged after parsing, skip files modified within `racyWindowNanos`,
// and cache nothing on a file system without `ctime`. Values are soft references because a parse holds PSI.
internal object ParsedFileCache {
    // Turning it off also clears it, so switching it back on never serves anything stored before.
    @Volatile
    var enabled: Boolean = true
        set(value) {
            field = value
            if (!value) entries.clear()
        }

    // Wider than the coarsest timestamp granularity (1 s on HFS+ and ext3), so a stored file's clock has moved past its mtime; only specs lower it.
    @Volatile
    var racyWindowNanos: Long = DEFAULT_RACY_WINDOW_NANOS

    // The values are soft but the keys are not; past the ceiling everything is dropped rather than evicted by age.
    private const val MAX_ENTRIES: Int = 50_000

    private class Entry(val stamp: FileStamp, val parsed: SoftReference<KoFileDeclaration>)

    private val entries = ConcurrentHashMap<String, Entry>()

    fun stampOf(absolutePath: String): FileStamp? {
        if (!enabled) return null
        val attributes = try {
            // Links are followed: the walk reports a link to a file as a file and Konsist reads
            // through it, so the stamp has to be the target's. The link's own never moves when
            // only the target is rewritten, and a link pointed elsewhere changes the inode.
            Files.readAttributes(
                Path.of(absolutePath),
                "unix:size,lastModifiedTime,ctime,fileKey",
            )
        } catch (_: UnsupportedOperationException) {
            return null
        } catch (_: IllegalArgumentException) {
            return null
        } catch (_: IOException) {
            return null
        } catch (_: SecurityException) {
            return null
        }
        val size = attributes["size"] as? Long ?: return null
        val modified = (attributes["lastModifiedTime"] as? FileTime)?.to(TimeUnit.NANOSECONDS) ?: return null
        val changed = (attributes["ctime"] as? FileTime)?.to(TimeUnit.NANOSECONDS) ?: return null
        val now = System.currentTimeMillis() * NANOS_PER_MILLI
        if (now - maxOf(modified, changed) < racyWindowNanos) return null
        return FileStamp(size = size, modifiedNanos = modified, changedNanos = changed, fileKey = attributes["fileKey"])
    }

    fun get(absolutePath: String, stamp: FileStamp): KoFileDeclaration? {
        if (!enabled) return null
        val entry = entries[absolutePath] ?: return null
        if (entry.stamp != stamp) return null
        return entry.parsed.get()
    }

    // Only for a stamp taken before the parse began: `stampOf` is asked again and nothing is stored unless the file still looks the same.
    fun put(absolutePath: String, stamp: FileStamp, parsed: KoFileDeclaration) {
        if (!enabled) return
        if (stampOf(absolutePath) != stamp) {
            entries.remove(absolutePath)
            return
        }
        if (entries.size >= MAX_ENTRIES) entries.clear()
        entries[absolutePath] = Entry(stamp, SoftReference(parsed))
    }

    fun clear() {
        entries.clear()
    }

    private const val NANOS_PER_MILLI: Long = 1_000_000

    // A `const` so that it is set before `racyWindowNanos` reads it.
    const val DEFAULT_RACY_WINDOW_NANOS: Long = 3_000_000_000
}
