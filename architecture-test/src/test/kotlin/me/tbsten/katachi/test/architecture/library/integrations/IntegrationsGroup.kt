package me.tbsten.katachi.test.architecture.library.integrations

import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.test.architecture.library.integrations.githubactions.gitHubActionsGroup

fun DeclarationContainerScope.integrationsGroup() = "Integrations".group {
    title = "連携"

    konsistModule()
    kontureModule()
    gradlePluginModule()
    cliModule()
    idePluginModule()
    agentSkillModule()
    gitHubActionsGroup()
}
