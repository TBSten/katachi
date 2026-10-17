package me.tbsten.katachi.intellij.ide.authoring

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import me.tbsten.katachi.intellij.ide.definition.DefinitionModelService
import me.tbsten.katachi.intellij.ide.definition.DefinitionPorts

/**
 * Where the features that help a person write a definition (completion first) get the definition of the
 * project. A project service so that a test replaces it (`project.replaceService`) with one over a fake
 * [DefinitionModelService]; the platform's is [DefinitionPorts], which creates nothing in a project without a
 * katachi definition module.
 *
 * ```kotlin
 * val definition = project.service<AuthoringPorts>().definition() ?: return
 * val snapshots = definition.definitions
 * ```
 */
@Service(Service.Level.PROJECT)
internal class AuthoringPorts(private val definitionOf: () -> DefinitionModelService?) {
    @Suppress("unused") // Called by the platform.
    constructor(project: Project) : this({ DefinitionPorts.of(project).service() })

    /** The definition service, or `null` in a project without katachi. */
    fun definition(): DefinitionModelService? = definitionOf()
}
