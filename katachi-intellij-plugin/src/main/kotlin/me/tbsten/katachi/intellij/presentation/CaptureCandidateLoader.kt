package me.tbsten.katachi.intellij.presentation

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.tbsten.katachi.intellij.data.placement.CaptureCandidates
import me.tbsten.katachi.intellij.data.placement.DirectoryLister
import me.tbsten.katachi.intellij.data.placement.PathPattern
import java.nio.file.Path
import kotlin.coroutines.CoroutineContext

/**
 * One capture field whose candidates are wanted: what [CaptureCandidates] needs, with the values entered so far.
 * [entered] is every capture and parameter value of the form, by name; only the levels above the capture matter.
 */
internal data class CandidateRequest(
    val field: FieldId,
    val pattern: PathPattern,
    val capture: String,
    val entered: Map<String, String>,
    val root: Path,
)

/**
 * Reads the candidates of capture fields in the background (design 3.3) and tells [onLoaded] with the result.
 *
 * Per field: a request waits [settle] (300 ms, so typing in bursts reads once), reads the disk on [ioContext], and delivers
 * only if no newer request for the field arrived meanwhile -- a stale result is dropped. A request whose key (the capture and
 * the levels above it, made concrete) equals the last one is ignored unless it is forced (the files changed, the definition
 * was reloaded). A field with nothing to offer (a level not entered yet, the file name level) is answered `null` at once,
 * without touching the disk. A failed read is `null` too, told to [onFailure]; cancelling is never swallowed.
 *
 * Thread-safe: callers may be on the EDT or a background thread, and a read finishes on [ioContext]'s thread. Every method
 * and the delivery take one lock ([lock]), and a result is delivered under it only if its job is still the field's job, so
 * a request, [forget] or [reloadAll] that wins the lock first always beats the result of an older read. [onLoaded] is
 * called under that lock, on whichever thread delivers: keep it short and never wait for another thread in it.
 *
 * ```kotlin
 * val loader = CaptureCandidateLoader(scope, IdeDirectoryLister(project)) { field, list -> dispatch(CandidatesLoaded(field, list)) }
 * loader.request(CandidateRequest(field, pattern, "feature", mapOf("name" to "Home"), root))
 * ```
 */
internal class CaptureCandidateLoader(
    private val scope: CoroutineScope,
    private val lister: DirectoryLister,
    private val ioContext: CoroutineContext = Dispatchers.Default,
    private val settle: suspend () -> Unit = { delay(SETTLE_MILLIS) },
    private val onFailure: (Throwable) -> Unit = {},
    private val onLoaded: (FieldId, CandidateList?) -> Unit,
) {
    private data class Key(val capture: String, val above: List<String>?, val root: Path, val pattern: String)

    /** Guards the three maps and the delivery; a caller that must act atomically with them (read state, then request) takes it too. */
    val lock: Any = Any()

    private val jobs = HashMap<FieldId, Job>()
    private val keys = HashMap<FieldId, Key>()
    private val latest = HashMap<FieldId, CandidateRequest>()

    /** Asks for [request]'s candidates; [force] reads again although nothing the candidates depend on changed. */
    fun request(request: CandidateRequest, force: Boolean = false): Unit = synchronized(lock) {
        val field = request.field
        latest[field] = request
        val above = CaptureCandidates.levelsAbove(request.pattern, request.capture, request.entered)
        val key = Key(request.capture, above, request.root, request.pattern.text)
        if (!force && keys[field] == key) return@synchronized
        keys[field] = key
        jobs.remove(field)?.cancel()
        if (above == null) {
            onLoaded(field, null)
            return@synchronized
        }
        val job = scope.launch(start = CoroutineStart.LAZY) {
            settle()
            val list = withContext(ioContext) { read(request) }
            synchronized(lock) {
                // Cancelling is cooperative and the read may have ended already: only the field's current job delivers.
                if (jobs[field] !== coroutineContext[Job]) return@launch
                jobs.remove(field)
                onLoaded(field, list)
            }
        }
        jobs[field] = job
        job.start()
    }

    /** Reads every requested field again: the files changed, or the definition was reloaded (design 3.3). */
    fun reloadAll(): Unit = synchronized(lock) {
        for (request in latest.values.toList()) request(request, force = true)
    }

    /** Stops caring about [field] (its row was unchecked, the dialog's template changed). */
    fun forget(field: FieldId): Unit = synchronized(lock) {
        jobs.remove(field)?.cancel()
        keys.remove(field)
        latest.remove(field)
    }

    private fun read(request: CandidateRequest): CandidateList? {
        val found = try {
            CaptureCandidates.of(request.pattern, request.capture, request.entered, request.root, lister, onFailure = onFailure)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onFailure(e)
            null
        } ?: return null
        return CandidateList(
            values = found.values,
            hiddenCount = found.hiddenCount,
            directoryPattern = CaptureCandidates.directoryPatternOf(request.pattern, request.capture, request.entered),
        )
    }

    companion object {
        /** The same wait as the dialog's existing-file check. */
        const val SETTLE_MILLIS: Long = 300
    }
}
