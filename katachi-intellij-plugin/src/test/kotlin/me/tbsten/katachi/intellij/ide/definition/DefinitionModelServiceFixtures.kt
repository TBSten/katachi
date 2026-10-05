package me.tbsten.katachi.intellij.ide.definition

import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.tbsten.katachi.intellij.model.DeclarationSiteRef
import me.tbsten.katachi.intellij.model.DefinitionSnapshot
import me.tbsten.katachi.intellij.model.DefinitionState
import me.tbsten.katachi.intellij.model.FileRoles
import me.tbsten.katachi.intellij.model.ModuleId
import me.tbsten.katachi.intellij.model.RoleFileDeclarations
import me.tbsten.katachi.intellij.model.answer
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A [DefinitionModelService] with nothing behind it, for the headless tests of the two features. The
 * test scripts what it answers and walks its state through a sequence.
 *
 * It keeps the contract of the real one: with no answer in the state (`NotLoaded`, or `Loading` /
 * `Unavailable` with no previous) `forFile` and `fileDeclarationsOf` give nothing, and the `freshness` of
 * what it returns is the state's, so a stale state shows up in the answers as it would in the IDE.
 *
 * ## A state sequence
 * [emit] sets the state; a collector of a `StateFlow` only sees the last of several emits made without
 * yielding. To have every state observed, step: [script] queues the states and [step] sets one, or [play]
 * sets each and runs a callback after it, which is where the test lets its collector run and asserts.
 *
 * ```kotlin
 * val service = FakeDefinitionModelService()
 * service.answer(path, roles)
 * service.play(DefinitionStateScripts.standardCycle(snapshot)) { state -> assertNoFlicker(feature, state) }
 * ```
 *
 * Files are matched by their `path` string (`/` separated, as a `VirtualFile` of the temp file system has it).
 *
 * This class sits in a `*Fixtures.kt` file, which is what the test layout calls what tests share, and not in `testing/`:
 * `uiTest` compiles `testing/` without the IntelliJ classes this needs (`VirtualFile`).
 */
internal class FakeDefinitionModelService(initial: DefinitionState = DefinitionState.NotLoaded) : DefinitionModelService {
    private val mutableState = MutableStateFlow(initial)
    override val state: StateFlow<DefinitionState> = mutableState.asStateFlow()

    private val roles = ConcurrentHashMap<String, FileRoles>()
    private val declarations = ConcurrentHashMap<String, List<RoleFileDeclarations>>()
    private val sites = ConcurrentHashMap<DeclarationSiteRef, ResolvedSite>()
    private val queued = ArrayDeque<DefinitionState>()
    private val mutableHistory = CopyOnWriteArrayList<DefinitionState>().apply { add(initial) }

    /** Every state the service has been in, the initial one first. */
    val history: List<DefinitionState> get() = mutableHistory.toList()

    /** How many times [ensureLoaded] was called. */
    @Volatile
    var ensureLoadedCalls: Int = 0
        private set

    private val mutableRefreshes = CopyOnWriteArrayList<RefreshReason>()

    /** The reasons [refresh] was called with, in order. */
    val refreshes: List<RefreshReason> get() = mutableRefreshes.toList()

    private val mutableAsked = CopyOnWriteArrayList<String>()

    /** The paths [forFile] / [forPath] / [fileDeclarationsOf] were asked about, in order. */
    val asked: List<String> get() = mutableAsked.toList()

    private val mutableResolved = CopyOnWriteArrayList<DeclarationSiteRef>()

    /** The sites [resolve] was asked about, in order. */
    val resolved: List<DeclarationSiteRef> get() = mutableResolved.toList()

    // --- the state ---

    /** Sets the state. Setting the state it is already in changes nothing (and is not in [history]), as a `StateFlow` would. */
    fun emit(state: DefinitionState) {
        if (mutableState.value == state) return
        mutableHistory += state
        mutableState.value = state
    }

    /** Sets each state in turn with no pause between them: a collector may see only the last. See [play]. */
    fun emit(vararg states: DefinitionState) = states.forEach { emit(it) }

    /** Queues [states] for [step]. */
    fun script(vararg states: DefinitionState) {
        queued.addAll(states)
    }

    /** Sets the next queued state. `false` when none is left. */
    fun step(): Boolean {
        val next = queued.removeFirstOrNull() ?: return false
        emit(next)
        return true
    }

    /** Sets each of [states] and calls [afterEach] after it, where the test lets its collectors run and asserts. */
    fun play(states: List<DefinitionState>, afterEach: (DefinitionState) -> Unit = {}) {
        for (next in states) {
            emit(next)
            afterEach(next)
        }
    }

    // --- scripted answers ---

    /**
     * What `forFile` / `forPath` answers for [path]. `null` is "no definition holds it", which is also what a
     * path that was never scripted gets; it removes an earlier answer (a `ConcurrentHashMap` takes no `null`).
     */
    fun answer(path: Path, roles: FileRoles?) {
        if (roles == null) this.roles.remove(keyOf(path)) else this.roles[keyOf(path)] = roles
    }

    /** What `fileDeclarationsOf` answers for [path]. */
    fun explain(path: Path, declarations: List<RoleFileDeclarations>) {
        this.declarations[keyOf(path)] = declarations
    }

    /** What `resolve` answers for [site]. A site that was not scripted is unresolved. */
    fun resolveWith(site: DeclarationSiteRef, resolved: ResolvedSite) {
        sites[site] = resolved
    }

    // --- DefinitionModelService ---

    override fun ensureLoaded() {
        ensureLoadedCalls++
    }

    override fun refresh(reason: RefreshReason) {
        mutableRefreshes += reason
    }

    override fun forFile(file: VirtualFile): FileRoles? = forKey(file.path)

    override fun forPath(path: Path): FileRoles? = forKey(keyOf(path))

    private fun forKey(key: String): FileRoles? {
        mutableAsked += key
        val answer = state.value.answer ?: return null
        return roles[key]?.copy(freshness = answer.freshness)
    }

    override fun definitionOf(module: ModuleId): DefinitionSnapshot? = definitions.firstOrNull { it.module.id == module }

    override val definitions: List<DefinitionSnapshot> get() = state.value.answer?.definitions.orEmpty()

    override fun resolve(module: ModuleId, site: DeclarationSiteRef): ResolvedSite {
        mutableResolved += site
        return sites[site] ?: ResolvedSite.Unresolved("not scripted in the fake: $site")
    }

    override fun fileDeclarationsOf(file: VirtualFile): List<RoleFileDeclarations> {
        mutableAsked += file.path
        val answer = state.value.answer ?: return emptyList()
        return declarations[file.path].orEmpty().map { it.copy(freshness = answer.freshness) }
    }

    private fun keyOf(path: Path): String = path.toString().replace('\\', '/')
}
