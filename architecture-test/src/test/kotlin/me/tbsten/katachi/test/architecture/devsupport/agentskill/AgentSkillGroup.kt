package me.tbsten.katachi.test.architecture.devsupport.agentskill

import me.tbsten.katachi.dsl.DeclarationContainerScope

fun DeclarationContainerScope.agentSkillGroup() = "AgentSkill".group {
    title = "エージェントスキル"

    agentSkills()
}
