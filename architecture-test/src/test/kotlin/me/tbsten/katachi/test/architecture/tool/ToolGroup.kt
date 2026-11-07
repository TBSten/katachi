package me.tbsten.katachi.test.architecture.tool

import me.tbsten.katachi.dsl.DeclarationContainerScope

fun DeclarationContainerScope.toolGroup() = "tool".group {
    documented = false
    title = "ツール設定"

    ci()
    gitHubAction()
    agentRule()
    git()
    ideRunConfiguration()
    ideDictionary()
    dokkaPlugin()
    benchmark()
    issueTemplate()
    pullRequestTemplate()
    installScript()
}
