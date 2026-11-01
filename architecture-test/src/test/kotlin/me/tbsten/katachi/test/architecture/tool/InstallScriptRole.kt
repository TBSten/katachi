package me.tbsten.katachi.test.architecture.tool

import me.tbsten.katachi.dsl.DeclarationContainerScope

fun DeclarationContainerScope.installScript() = "InstallScript".role {
    title = "インストールスクリプト"
    summary = "katachi を別のプロジェクトに導入する Kotlin script（下書き）"
    example("install-katachi.main.kts", "setupKatachi { } で導入の内容を書く")
    layout {
        "install-katachi.main.kts".file()
    }
}
