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

internal data class CandidateRequest(
    val field: FieldId,
    val pattern: PathPattern,
    val capture: String,
    val entered: Map<String, String>,
    val root: Path,
)

// Thread-safe: every method and the delivery take one lock, and onLoaded is called under it: keep it short and never wait for another thread in it.
internal class CaptureCandidateLoader(
    private val scope: CoroutineScope,
    private val lister: DirectoryLister,
    private val ioContext: CoroutineContext = Dispatchers.Default,
    private val settle: suspend () -> Unit = { delay(SETTLE_MILLIS) },
    private val onFailure: (Throwable) -> Unit = {},
    private val onLoaded: (FieldId, CandidateList?) -> Unit,
) {
    private data class Key(val capture: String, val above: List<String>?, val root: Path, val pattern: String)

    // A caller that must act atomically with the three maps (read state, then request) takes this lock too.
    val lock: Any = Any()

    private val jobs = HashMap<FieldId, Job>()
    private val keys = HashMap<FieldId, Key>()
    private val latest = HashMap<FieldId, CandidateRequest>()

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

    fun reloadAll(): Unit = synchronized(lock) {
        for (request in latest.values.toList()) request(request, force = true)
    }

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
        const val SETTLE_MILLIS: Long = 300
    }
}
