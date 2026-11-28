package me.tbsten.katachi.test.architecture.devsupport.versioncontrol.github

import me.tbsten.katachi.dsl.DeclarationContainerScope

fun DeclarationContainerScope.gitHubGroup() = "GitHub".group {
    title = "GitHub"

    issueTemplate()
    pullRequestTemplate()
    githubActions()
}
