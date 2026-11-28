package me.tbsten.katachi.test.architecture.devsupport.aiagent

import me.tbsten.katachi.dsl.DeclarationContainerScope

fun DeclarationContainerScope.aiAgentGroup() = "AiAgent".group {
    title = "AI エージェント"

    agentRule()
}
