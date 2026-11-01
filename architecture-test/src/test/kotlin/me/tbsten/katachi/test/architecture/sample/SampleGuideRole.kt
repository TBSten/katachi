package me.tbsten.katachi.test.architecture.sample

import me.tbsten.katachi.dsl.DeclarationContainerScope

fun DeclarationContainerScope.sampleGuide() = "SampleGuide".role {
    title = "サンプルの案内"
    summary = "4つのサンプルに共通する方針・回し方・ビルド設定"
    example("sample/README.md", "サンプルの一覧、全サンプルの回し方、Android SDK の用意")
    layout {
        "sample" {
            "README.md".file()
        }
    }
}
