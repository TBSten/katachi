package me.tbsten.katachi.intellij.ide.definition

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.flow.StateFlow
import me.tbsten.katachi.intellij.data.definition.DefinitionLookup
import me.tbsten.katachi.intellij.ide.KatachiProjectService
import me.tbsten.katachi.intellij.ide.sdkCall
import me.tbsten.katachi.intellij.model.DeclarationSiteRef
import me.tbsten.katachi.intellij.model.DefinitionErrors
import me.tbsten.katachi.intellij.model.DefinitionSnapshot
import me.tbsten.katachi.intellij.model.DefinitionState
import me.tbsten.katachi.intellij.model.FileRoles
import me.tbsten.katachi.intellij.model.KatachiModule
import me.tbsten.katachi.intellij.model.MatchOutcome
import me.tbsten.katachi.intellij.model.ModuleId
import me.tbsten.katachi.intellij.model.RoleFileDeclarations
import me.tbsten.katachi.intellij.model.answer
import java.nio.file.Path

/**
 * The real [DefinitionModelService] (design 4.5): a thin facade over [KatachiProjectService.definitions],
 * the definition derived from the tool window's one load. Questions about a file are pure path matches
 * ([DefinitionLookup]) on the latest answer, kept until the answer changes, so the gutter and the
 * notification may ask the same file many times. A file the version control ignores while katachi sees
 * only tracked files is [MatchOutcome.IgnoredByVcs] instead of unexpected (design 4.4, U-3).
 *
 * Created by [DefinitionPorts] only for a project with a definition module (decision 17).
 *
 * ```kotlin
 * val service = DefinitionPorts.of(project).service() ?: return
 * service.ensureLoaded()
 * val roles = service.forFile(file)
 * ```
 */
@Service(Service.Level.PROJECT)
internal class KatachiDefinitionService(
    private val projectService: KatachiProjectService,
    private val ignoredByVcs: (VirtualFile) -> Boolean,
) : DefinitionModelService {
    @Suppress("unused") // Called by the platform.
    constructor(project: Project) : this(KatachiProjectService.getInstance(project), { file -> isIgnoredByVcs(project, file) })

    @Volatile private var lookup: DefinitionLookup? = null

    override val state: StateFlow<DefinitionState> get() = projectService.definitions

    override val definitionErrors: StateFlow<DefinitionErrors> get() = projectService.definitionErrors

    override fun ensureLoaded() = projectService.ensureLoaded()

    override fun refresh(reason: RefreshReason) = projectService.refreshDefinition(reason)

    override fun forFile(file: VirtualFile): FileRoles? {
        val roles = forPath(pathOf(file) ?: return null) ?: return null
        val unexpected = roles.outcome is MatchOutcome.Unexpected || roles.outcome is MatchOutcome.UnexpectedUnder || roles.outcome is MatchOutcome.CaptureMismatch
        val tracksOnlyGit = definitionOf(roles.module.id)?.definition?.architecture?.isGitTracked == true
        return if (unexpected && tracksOnlyGit && ignoredByVcs(file)) roles.copy(outcome = MatchOutcome.IgnoredByVcs, nearby = emptyList()) else roles
    }

    override fun forPath(path: Path): FileRoles? {
        val answer = state.value.answer ?: return null
        return lookupOf(answer.definitions).forPath(path, answer.freshness)
    }

    override fun definitionOf(module: ModuleId): DefinitionSnapshot? = definitions.firstOrNull { it.module.id == module }

    override val definitions: List<DefinitionSnapshot> get() = state.value.answer?.definitions.orEmpty()

    override fun resolve(module: ModuleId, site: DeclarationSiteRef): ResolvedSite {
        val known = moduleOf(module) ?: return ResolvedSite.Unresolved("no definition module $module is known")
        return projectService.siteResolver.resolve(known, site)
    }

    override fun definitionModuleOf(file: VirtualFile): ModuleId? {
        val path = pathOf(file) ?: return null
        return knownModules().firstOrNull { module -> projectService.siteResolver.testRootsOf(module).any { path.startsWith(it) } }?.id
    }

    /**
     * The definition modules the project knows: those with an answer, then those only detected. A definition that was never
     * read (the first load was rejected, and there is no cache) has no answer, yet its mistakes are shown in its own files.
     */
    private fun knownModules(): List<KatachiModule> =
        (definitions.map { it.module } + projectService.viewModel.state.value.modules).distinctBy { it.id }

    private fun moduleOf(id: ModuleId): KatachiModule? = knownModules().firstOrNull { it.id == id }

    override fun fileDeclarationsOf(file: VirtualFile): List<RoleFileDeclarations> {
        val answer = state.value.answer ?: return emptyList()
        val path = pathOf(file) ?: return emptyList()
        return lookupOf(answer.definitions).fileDeclarationsOf(path, answer.freshness)
    }

    /** The lookup of [definitions], made again only when the answer's list is another one. */
    private fun lookupOf(definitions: List<DefinitionSnapshot>): DefinitionLookup =
        lookup?.takeIf { it.definitions === definitions } ?: DefinitionLookup(definitions).also { lookup = it }

    private fun pathOf(file: VirtualFile): Path? = sdkCall("read the path of $file") { file.fileSystem.getNioPath(file) }.getOrNull()

    private companion object {
        /** `ChangeListManager.isIgnoredFile` works in dumb mode; a failure reads as "not ignored". */
        fun isIgnoredByVcs(project: Project, file: VirtualFile): Boolean =
            sdkCall("ask the VCS whether $file is ignored") { ChangeListManager.getInstance(project).isIgnoredFile(file) }.getOrNull() == true
    }
}
