package me.tbsten.katachi.test.architecture.devsupport

import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.test.architecture.devsupport.versioncontrol.versionControlGroup
import me.tbsten.katachi.test.architecture.devsupport.aiagent.aiAgentGroup
import me.tbsten.katachi.test.architecture.devsupport.agentskill.agentSkillGroup
import me.tbsten.katachi.test.architecture.devsupport.ideconfiguration.ideConfigurationGroup
import me.tbsten.katachi.test.architecture.devsupport.buildsystem.buildSystemGroup

fun DeclarationContainerScope.devSupportGroup() = "DevSupport".group {
    title = "開発の支え"
    documented = false

    versionControlGroup()
    aiAgentGroup()
    agentSkillGroup()
    ideConfigurationGroup()
    buildSystemGroup()
}
