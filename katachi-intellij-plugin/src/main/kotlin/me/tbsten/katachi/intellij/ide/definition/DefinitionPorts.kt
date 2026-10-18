package me.tbsten.katachi.intellij.ide.definition

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import me.tbsten.katachi.intellij.ide.DefinitionModulePresence
import me.tbsten.katachi.intellij.ide.KatachiProjectService
import me.tbsten.katachi.intellij.ide.sarif.KatachiViolationsService
import me.tbsten.katachi.intellij.ide.sarif.ViolationsService
import me.tbsten.katachi.intellij.ide.sdkCall

/**
 * Where the two features (the gutter, the rules of the open file) get the definition and the violations
 * of a project (design 4.5). As with the notification's ports, the services exist only for a project
 * whose synced data has a katachi definition module (decision 17): asking in any other project creates
 * nothing and runs no Gradle. A feature's test builds one over fakes instead of [of].
 *
 * Every function may be called from any thread and does not throw; the SDK calls are in `sdkCall`.
 *
 * ```kotlin
 * val definition = DefinitionPorts.of(project).service() ?: return null
 * definition.ensureLoaded()
 * ```
 */
internal class DefinitionPorts(
    /** Whether the katachi project service already exists (something of katachi runs here). */
    private val serviceExists: () -> Boolean,
    /** Whether the synced data has a definition module; reads it, never runs Gradle. */
    val hasDefinitionModule: () -> Boolean,
    // Not named `definition` / `violations`: inside `violations()` the property would be shadowed by the function and call itself.
    private val createDefinition: () -> DefinitionModelService,
    private val createViolations: () -> ViolationsService,
) {
    /** The definition service, created on the first call, or `null` in a project without katachi. */
    fun service(): DefinitionModelService? = if (available()) sdkCall("create the katachi definition service") { createDefinition() }.getOrNull() else null

    /** The violations service, created on the first call, or `null` in a project without katachi. */
    fun violations(): ViolationsService? = if (available()) sdkCall("create the katachi violations service") { createViolations() }.getOrNull() else null

    /** Whether this project has katachi: its service runs, or the synced data has a definition module. */
    fun available(): Boolean = serviceExists() || hasDefinitionModule()

    companion object {
        fun of(project: Project): DefinitionPorts = DefinitionPorts(
            serviceExists = { sdkCall("look for the katachi service") { project.getServiceIfCreated(KatachiProjectService::class.java) != null }.getOrNull() == true },
            hasDefinitionModule = { sdkCall("read whether the katachi definition module exists") { DefinitionModulePresence.getInstance(project).hasDefinitionModule() }.getOrNull() == true },
            createDefinition = { project.service<KatachiDefinitionService>() },
            createViolations = { project.service<KatachiViolationsService>() },
        )
    }
}
