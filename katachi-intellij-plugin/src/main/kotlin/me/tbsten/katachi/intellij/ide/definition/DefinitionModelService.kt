package me.tbsten.katachi.intellij.ide.definition

import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.flow.StateFlow
import me.tbsten.katachi.intellij.model.DeclarationSiteRef
import me.tbsten.katachi.intellij.model.DefinitionSnapshot
import me.tbsten.katachi.intellij.model.DefinitionState
import me.tbsten.katachi.intellij.model.FileRoles
import me.tbsten.katachi.intellij.model.ModuleId
import me.tbsten.katachi.intellij.model.RoleFileDeclarations
import java.nio.file.Path

/** What asked for a refresh of the definition. */
internal enum class RefreshReason { UserAction, SyncCompleted, DefinitionChanged }

/**
 * The architecture definition as the IDE knows it (design 4.5): the state of loading it, which role
 * holds a file, where a declaration was written. The two features (the gutter, the rules for the open
 * file) read the definition only through this; the real one is `KatachiDefinitionService`, a fake
 * stands in for it in their tests.
 *
 * Nothing here takes a read lock or touches an index: ask from a background thread. An answer is given
 * while the state is `Ready`, `Loading`, a stale `Ready` or an `Unavailable` that still carries one;
 * the model is never blocked on Gradle. The implementation guards its SDK calls (`VirtualFile` to `Path`, the VCS) with `sdkCall`.
 *
 * ```kotlin
 * service.ensureLoaded()
 * val roles = service.forFile(file) ?: return          // null: not under a definition, or nothing loaded
 * val names = roles.matches.map { it.role.qualifiedName }
 * ```
 */
internal interface DefinitionModelService {
    /** The state now. Readable from any thread; a change is received by collecting it. */
    val state: StateFlow<DefinitionState>

    /** Starts reading the definition if nothing was read. Safe to call any number of times; does not wait. */
    fun ensureLoaded()

    /** An explicit update: runs Gradle again. While it runs the state is `Loading(previous)`. */
    fun refresh(reason: RefreshReason)

    /**
     * Which roles, entries and constraints hold [file]. `null` when no definition module's project root is
     * above it, or when there is no answer yet. For nested roots the deeper definition answers.
     */
    fun forFile(file: VirtualFile): FileRoles?

    /** [forFile] for a path: a pure match of the path against the definition. */
    fun forPath(path: Path): FileRoles?

    /** One definition module's snapshot (the model, its templates, when it was loaded), if there is an answer for it. */
    fun definitionOf(module: ModuleId): DefinitionSnapshot?

    /** The snapshots of the latest answer, whether it is `Ready`, `Loading` or stale. */
    val definitions: List<DefinitionSnapshot>

    /** Finds a `FileName.kt:NN` of [module]'s definition among its test source roots. Zero or several matches are `Unresolved`. */
    fun resolve(module: ModuleId, site: DeclarationSiteRef): ResolvedSite

    /**
     * The explanation of [file]'s top-level declarations under each `ktFileContent { }` constraint that covers it
     * (design 1.7.2), one per role. Empty when no role holds it, the file is not `.kt`, or nothing explains it.
     */
    fun fileDeclarationsOf(file: VirtualFile): List<RoleFileDeclarations>
}
