package com.example.kmp.roles

import me.tbsten.katachi.dsl.DeclarationContainerScope

/**
 * The iOS application, which Xcode builds and Gradle knows nothing about.
 *
 * `app/ios` is not a Gradle module, so it is not written as one: there is no module path that
 * resolves to it and no `build.gradle.kts` to require. It stays a plain directory key, which
 * is the whole point of having it in this sample. Xcode owns what is inside it, so the role
 * declares the directory and stops the check there. It is still a declared directory with a
 * role and a summary around it, which is the only way katachi lets anything go unchecked.
 */
fun DeclarationContainerScope.xcodeProject() = "XcodeProject" {
    title = "Xcode プロジェクト"
    summary = "app/ios 以下。Gradle の管理外で、検査もしない"
    description = """
        iOS アプリの側です。`app/ios` は Gradle モジュールではありません。
        `settings.gradle.kts` が意図的に include していないので、モジュールパスで書くことが
        できず、`build.gradle.kts` を要求することもできません。ただのディレクトリキーとして
        宣言し、`ignore()` で中の検査を止めています。

        検査しないのに宣言する、というのがこの役割の見せどころです。katachi で「ここは見ない」と
        言う唯一の方法が、役割と summary を持つディレクトリとして宣言することだからです。
        何も書かなければ `app/ios` 以下のすべてが「どの役割も名乗っていないファイル」になります。

        中にあるのは Swift のソースと `Info.plist`、つまり iOS アプリの形だけです。
        `iosApp.xcodeproj/` はコミットしていません。手書きの `project.pbxproj` は Xcode が
        開けない壊れ方をするうえ、このサンプルは iOS 向けにビルドしないので置いても死荷重に
        なります。実際に動かしたければ Xcode で作ってください。

        置いてよいもの:

        - Xcode が持つもの。Swift のソース、`Info.plist`、アセットカタログ

        置いてはいけないもの:

        - Kotlin のコード。共有したいコードは `:data` のような KMP モジュールに置き、
          framework として渡します（このサンプルでは設定していません）
    """.trimIndent()
    example("iosAppApp.swift", "SwiftUI のエントリポイント")
    example("ContentView.swift", "iOS 側の画面")
    layout {
        "app/ios" { ignore() }
    }
}
