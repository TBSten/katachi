package me.tbsten.katachi.test.architecture.devsupport.buildsystem

import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.gradle

fun DeclarationContainerScope.buildSystemGroup() = "BuildSystem".group {
    title = "ビルドシステム"
    description = """
        `gradle { }` が Gradle の設定ファイル一式（SettingsScript・BuildScript・GradleProperties・VersionCatalog・DaemonJvmProperties・GradleWrapper）を宣言し、
        buildSrc だけをここの BuildLogic が足します。
    """.trimIndent()

    gradle {
        buildLogic()
    }
}
