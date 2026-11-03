package me.tbsten.katachi.intellij.ide.definition

import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.tbsten.katachi.intellij.model.DeclarationAt
import me.tbsten.katachi.intellij.model.DeclarationSiteRef
import me.tbsten.katachi.intellij.model.DefinitionErrors
import me.tbsten.katachi.intellij.model.DefinitionSnapshot
import me.tbsten.katachi.intellij.model.DefinitionState
import me.tbsten.katachi.intellij.model.FileRoles
import me.tbsten.katachi.intellij.model.ModuleId
import me.tbsten.katachi.intellij.model.RoleFileDeclarations
import me.tbsten.katachi.intellij.model.declarations
import java.nio.file.Path

internal enum class RefreshReason { UserAction, SyncCompleted, DefinitionChanged }

// Ask from a background thread: nothing here takes a read lock, and an answer never blocks on Gradle.
internal interface DefinitionModelService {
    val state: StateFlow<DefinitionState>

    val definitionErrors: StateFlow<DefinitionErrors> get() = NO_DEFINITION_ERRORS

    fun ensureLoaded()

    fun refresh(reason: RefreshReason)

    fun forFile(file: VirtualFile): FileRoles?

    fun forPath(path: Path): FileRoles?

    fun definitionOf(module: ModuleId): DefinitionSnapshot?

    val definitions: List<DefinitionSnapshot>

    fun resolve(module: ModuleId, site: DeclarationSiteRef): ResolvedSite

    fun definitionModuleOf(file: VirtualFile): ModuleId? = null

    fun declarationsIn(file: VirtualFile): List<DeclarationAt> {
        val module = definitionModuleOf(file) ?: return emptyList()
        val definition = definitionOf(module)?.definition ?: return emptyList()
        val found = HashMap<DeclarationSiteRef, Boolean>()
        return definition.declarations(module).filter { declaration ->
            declaration.site.fileName == file.name &&
                found.getOrPut(declaration.site) { (resolve(module, declaration.site) as? ResolvedSite.Found)?.file == file }
        }
    }

    fun fileDeclarationsOf(file: VirtualFile): List<RoleFileDeclarations>
}

private val NO_DEFINITION_ERRORS: StateFlow<DefinitionErrors> = MutableStateFlow(DefinitionErrors.None).asStateFlow()
