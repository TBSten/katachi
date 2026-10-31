package me.tbsten.katachi.test.architecture.roles

import me.tbsten.katachi.dsl.DeclarationContainerScope

/**
 * The role of the Kotlin script that sets katachi up in another project.
 *
 * Still a draft: the script is a sketch of the setup DSL and is not wired to anything yet.
 */
fun DeclarationContainerScope.installScript() = "InstallScript".role {
    title = "インストールスクリプト"
    summary = "katachi を別のプロジェクトに導入する Kotlin script（下書き）"
    example("install-katachi.main.kts", "setupKatachi { } で導入の内容を書く")
    layout {
        "install-katachi.main.kts".file()
    }
}
