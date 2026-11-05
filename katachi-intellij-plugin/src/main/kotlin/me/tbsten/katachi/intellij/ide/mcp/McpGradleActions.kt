package me.tbsten.katachi.intellij.ide.mcp

import com.intellij.openapi.project.Project
import me.tbsten.katachi.intellij.data.generate.HeadlessGenerationRequest
import me.tbsten.katachi.intellij.data.generate.HeadlessGenerationResult
import me.tbsten.katachi.intellij.ide.DefinitionReload
import me.tbsten.katachi.intellij.ide.KatachiProjectService
import me.tbsten.katachi.intellij.ide.KatachiSettings
import me.tbsten.katachi.intellij.ide.definition.DefinitionPorts
import me.tbsten.katachi.intellij.ide.sarif.RunCheckAction
import me.tbsten.katachi.intellij.ide.sdkCall
import me.tbsten.katachi.intellij.model.KatachiModule
import me.tbsten.katachi.intellij.model.ModuleId

/** What the Gradle tools need of the project besides the definition and violations services. */
internal interface McpGradleActions {
    fun definitionModules(): List<KatachiModule>

    suspend fun reload(module: KatachiModule?): DefinitionReload

    /** Saves the open documents and picks the module the check runs on when none is named, as Run Architecture Check does. */
    suspend fun saveAndPickModule(): ModuleId?

    suspend fun saveDocuments()

    /** Settings | Tools | katachi lets AI agents generate files. */
    fun allowsGenerate(): Boolean

    suspend fun generate(request: HeadlessGenerationRequest): HeadlessGenerationResult

    companion object {
        fun of(project: Project): McpGradleActions = ProjectGradleActions(project)
    }
}

internal class ProjectGradleActions(private val project: Project) : McpGradleActions {
    private val service: KatachiProjectService get() = KatachiProjectService.getInstance(project)

    override fun definitionModules(): List<KatachiModule> = service.definitionModules()

    override suspend fun reload(module: KatachiModule?): DefinitionReload = service.reloadAndAwait(module)

    override suspend fun saveAndPickModule(): ModuleId? {
        val definition = DefinitionPorts.of(project).service() ?: return null
        return RunCheckAction.saveAndPickModule(project, definition, file = null)
    }

    override suspend fun saveDocuments() = RunCheckAction.saveDocumentsForCheck()

    override fun allowsGenerate(): Boolean =
        sdkCall("read whether MCP may generate") { KatachiSettings.getInstance(project).mcpAllowGenerate }.getOrNull() == true

    override suspend fun generate(request: HeadlessGenerationRequest): HeadlessGenerationResult = service.headlessGeneration.run(request)
}
