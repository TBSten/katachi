package me.tbsten.katachi.konsist.internal

import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import java.lang.ref.SoftReference
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * What a file looked like on disk when it was stat'ed: the key a parse is filed under.
 *
 * Size and mtime are what the cache is asked to key on. `ctime` and the inode are added
 * because both are cheap and neither can be set back by a tool: `cp -p`, `rsync -t` and
 * `touch -r` can restore an mtime, but a write always moves the ctime, and a replace by
 * rename always changes the inode.
 */
internal data class FileStamp(
    val size: Long,
    val modifiedNanos: Long,
    val changedNanos: Long,
    val fileKey: Any?,
)

/**
 * Konsist's parse of single files, kept for the life of the JVM and keyed by what the file
 * looked like on disk.
 *
 * A second `assert()` in the same JVM — another test class, or a role whose directories overlap
 * one already checked — asks for files that have not changed since, and parsing is what costs.
 * The per-run memo in [konsistScopeOf] cannot help there; it is thrown away with the run.
 *
 * Staleness is the one thing this must never produce: an old parse returned for new contents
 * would let a constraint pass over code it never saw. So a parse is only ever stored under a
 * stamp taken before parsing and taken again, unchanged, after it; a file modified within
 * [racyWindowNanos] of the stat is not stored at all, because a second write inside the same
 * timestamp tick would keep both size and mtime; and a file system without `ctime` gets no
 * cache. Values are [SoftReference]s because a parse holds PSI: under memory pressure the GC
 * takes it back and the next run reparses.
 */
internal object ParsedFileCache {
    /**
     * `false` turns the cache off for every caller in this JVM, lookups and stores alike.
     *
     * For katachi's own specs that rewrite a fixture and want each run to parse what is on
     * disk without reasoning about stamps. Clearing is part of turning it off, so switching it
     * back on never serves anything stored before.
     */
    @Volatile
    var enabled: Boolean = true
        set(value) {
            field = value
            if (!value) entries.clear()
        }

    /**
     * How recent an mtime has to be for the file to be left out of the cache.
     *
     * Wider than the coarsest timestamp granularity katachi is likely to meet (1 s on HFS+ and
     * ext3), so a file stored here has a clock that has already moved past its mtime, and any
     * later write shows up as a different one. Only a spec lowers it, to exercise the cache on
     * files it has just written; setting an mtime back does not help there, as that moves ctime.
     */
    @Volatile
    var racyWindowNanos: Long = DEFAULT_RACY_WINDOW_NANOS

    /**
     * A ceiling on how many paths are remembered.
     *
     * The values are soft, but the keys are not; a JVM that walks thousands of throwaway
     * fixture directories would otherwise keep a key for each. Past the ceiling everything is
     * dropped rather than evicting by age: this is a cache, and a miss only costs a parse.
     */
    private const val MAX_ENTRIES: Int = 50_000

    private class Entry(val stamp: FileStamp, val parsed: SoftReference<KoFileDeclaration>)

    private val entries = ConcurrentHashMap<String, Entry>()

    /**
     * The stamp of [absolutePath] now, or `null` when it must not take part in the cache.
     *
     * `null` covers a disabled cache, a file that cannot be stat'ed, a file system that does
     * not report `ctime`, and a file modified too recently to be told apart from its next write.
     */
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

    /** The parse filed under exactly [stamp], or `null` when there is none or it was collected. */
    fun get(absolutePath: String, stamp: FileStamp): KoFileDeclaration? {
        if (!enabled) return null
        val entry = entries[absolutePath] ?: return null
        if (entry.stamp != stamp) return null
        return entry.parsed.get()
    }

    /**
     * Files [parsed] under [stamp], replacing whatever an older version of the file left.
     *
     * Only for a stamp taken before the parse began; [stampOf] is asked again here and nothing
     * is stored unless the file still looks the same, so a write that landed during the parse
     * is never filed under the stamp of the version before it.
     */
    fun put(absolutePath: String, stamp: FileStamp, parsed: KoFileDeclaration) {
        if (!enabled) return
        if (stampOf(absolutePath) != stamp) {
            entries.remove(absolutePath)
            return
        }
        if (entries.size >= MAX_ENTRIES) entries.clear()
        entries[absolutePath] = Entry(stamp, SoftReference(parsed))
    }

    /** Forgets every parse. */
    fun clear() {
        entries.clear()
    }

    private const val NANOS_PER_MILLI: Long = 1_000_000

    /** Three seconds. A `const` so that it is set before [racyWindowNanos] reads it. */
    const val DEFAULT_RACY_WINDOW_NANOS: Long = 3_000_000_000
}
