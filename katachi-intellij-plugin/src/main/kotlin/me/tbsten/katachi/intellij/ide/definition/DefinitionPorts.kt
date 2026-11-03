package me.tbsten.katachi.intellij.ide.definition

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import me.tbsten.katachi.intellij.ide.DefinitionModulePresence
import me.tbsten.katachi.intellij.ide.KatachiProjectService
import me.tbsten.katachi.intellij.ide.sarif.KatachiViolationsService
import me.tbsten.katachi.intellij.ide.sarif.ViolationsService
import me.tbsten.katachi.intellij.ide.sdkCall

// Services exist only for a project with a definition module: asking elsewhere creates nothing and runs no Gradle.
internal class DefinitionPorts(
    private val serviceExists: () -> Boolean,
    val hasDefinitionModule: () -> Boolean,
    // Not named `definition` / `violations`: inside `violations()` the property would be shadowed by the function and call itself.
    private val createDefinition: () -> DefinitionModelService,
    private val createViolations: () -> ViolationsService,
) {
    fun service(): DefinitionModelService? = if (available()) sdkCall("create the katachi definition service") { createDefinition() }.getOrNull() else null

    fun violations(): ViolationsService? = if (available()) sdkCall("create the katachi violations service") { createViolations() }.getOrNull() else null

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
