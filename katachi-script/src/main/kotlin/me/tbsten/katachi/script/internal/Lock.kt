package me.tbsten.katachi.script.internal

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.FileTime
import java.time.Duration
import java.time.Instant
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * The kit's write lock, kept compatible with the shell script so that a `katachi data merge`
 * and a `sh katachi-install.sh add` (a working directory set up with the old install kit) running
 * side by side still take turns: a directory made with
 * `mkdir`, a `pid` file inside, and the same place for it (`<workdir>/tmp/cache/.lock` for a file
 * under the working directory, `<file>.lock` otherwise).
 */
internal class WorkdirLock private constructor(private val directory: Path) {
    fun release() {
        remove()
    }

    private fun remove() {
        directory.resolve("pid").deleteIfExists()
        directory.resolve("pid.tmp").deleteIfExists()
        runCatching { Files.deleteIfExists(directory) }
    }

    companion object {
        private const val DEFAULT_WAIT_SECONDS = 30L

        private val STALE_AFTER: Duration = Duration.ofSeconds(60)

        fun pathFor(context: ScriptContext, target: Path): Path {
            val parent = target.normalize().parent ?: context.cwd
            val workdir = context.findWorkdir()
            if (workdir != null && parent.startsWith(workdir)) return workdir.resolve("tmp/cache/.lock")
            return parent.resolve("${target.fileName}.lock")
        }

        fun acquire(context: ScriptContext, target: Path): WorkdirLock {
            val directory = pathFor(context, target)
            val wait = context.env["KATACHI_LOCK_WAIT"]?.toLongOrNull() ?: DEFAULT_WAIT_SECONDS
            directory.parent?.createDirectories()
            var waited = 0L
            while (true) {
                if (tryMake(directory)) {
                    val tmp = directory.resolve("pid.tmp")
                    tmp.writeText(ProcessHandle.current().pid().toString())
                    Files.move(tmp, directory.resolve("pid"), StandardCopyOption.ATOMIC_MOVE)
                    return WorkdirLock(directory)
                }
                if (isStale(directory)) {
                    WorkdirLock(directory).remove()
                    continue
                }
                if (waited >= wait) {
                    die(
                        "作業用ディレクトリのロックを $wait 秒待ちましたが取れませんでした: $directory 。ほかの katachi が書き込み中か、残骸です。残骸なら rm -r で消してやり直してください",
                        "Waited $wait seconds for the lock but could not get it: $directory . Another katachi is writing, or this is left over. If it is left over, remove it with rm -r and try again",
                    )
                }
                Thread.sleep(1000)
                waited += 1
            }
        }

        private fun tryMake(directory: Path): Boolean = try {
            Files.createDirectory(directory)
            true
        } catch (_: IOException) {
            false
        }

        private fun isStale(directory: Path): Boolean {
            val pidFile = directory.resolve("pid")
            val pid = if (pidFile.isRegularFile()) pidFile.readText().trim().toLongOrNull() else null
            if (pid == null) {
                if (!directory.exists()) return false
                val modified: FileTime = directory.getLastModifiedTime()
                return Duration.between(modified.toInstant(), Instant.now()) > STALE_AFTER
            }
            return ProcessHandle.of(pid).map { !it.isAlive }.orElse(true)
        }
    }
}

/**
 * Runs [block] holding the lock for [target]. A command that already holds it (an `add` that
 * read the JSON before writing it back) does not take it again; only the one that took it lets go.
 */
internal inline fun <T> ScriptContext.withLock(target: Path, block: () -> T): T {
    if (heldLock != null) return block()
    val lock = WorkdirLock.acquire(this, target)
    heldLock = lock
    try {
        return block()
    } finally {
        heldLock = null
        lock.release()
    }
}
