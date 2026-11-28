package me.tbsten.katachi.test.architecture.devsupport.ideconfiguration

import me.tbsten.katachi.dsl.DeclarationContainerScope

fun DeclarationContainerScope.ideConfigurationGroup() = "IdeConfiguration".group {
    title = "IDE 設定"

    runConfiguration()
    dictionary()
}
