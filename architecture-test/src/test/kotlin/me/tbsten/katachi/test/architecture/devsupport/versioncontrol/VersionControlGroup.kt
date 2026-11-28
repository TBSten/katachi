package me.tbsten.katachi.test.architecture.devsupport.versioncontrol

import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.test.architecture.devsupport.versioncontrol.github.gitHubGroup

fun DeclarationContainerScope.versionControlGroup() = "VersionControl".group {
    title = "バージョン管理"

    git()
    gitHubGroup()
}
