package me.tbsten.katachi.test.architecture.roles

import me.tbsten.katachi.dsl.DeclarationContainerScope

/**
 * The role of the page that says what the four samples have in common.
 *
 * Its own role rather than a line in [sampleBuild]: that role declares the samples as builds
 * and ignores their insides, and a README is a document a reader opens, not a build.
 */
fun DeclarationContainerScope.sampleGuide() = "SampleGuide" {
    title = "サンプルの案内"
    summary = "4つのサンプルに共通する方針・回し方・ビルド設定"
    example("sample/README.md", "サンプルの一覧、全サンプルの回し方、Android SDK の用意")
    layout {
        "sample" {
            "README.md".file()
        }
    }
}
