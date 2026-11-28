package me.tbsten.katachi.test.architecture.library.integrations.githubactions

import me.tbsten.katachi.dsl.DeclarationContainerScope

fun DeclarationContainerScope.gitHubActionsGroup() = "GitHubActions".group {
    title = "GitHub Actions"

    compositeAction()
}
