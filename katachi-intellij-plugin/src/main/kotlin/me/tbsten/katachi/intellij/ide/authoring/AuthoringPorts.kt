package me.tbsten.katachi.intellij.ide.authoring

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import me.tbsten.katachi.intellij.ide.definition.DefinitionModelService
import me.tbsten.katachi.intellij.ide.definition.DefinitionPorts

// Project service so that tests can replace it with `project.replaceService`.
@Service(Service.Level.PROJECT)
internal class AuthoringPorts(private val definitionOf: () -> DefinitionModelService?) {
    @Suppress("unused") // Called by the platform.
    constructor(project: Project) : this({ DefinitionPorts.of(project).service() })

    fun definition(): DefinitionModelService? = definitionOf()
}
