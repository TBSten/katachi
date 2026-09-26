package com.example.roles

import com.example.forbiddenContents
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

/** The role of the katachi definition itself, which belongs to no layer of the application. */
fun DeclarationContainerScope.architectureDefinition() = "ArchitectureDefinition" {
    title = "アーキテクチャ定義"
    summary = "katachi の DSL で書かれた役割の定義。どのレイヤーにも属さない"
    description = """
        このプロジェクトの形を書いたコードそのものです。アプリのどのレイヤーにも属さないので、
        `:architecture-test` という専用モジュールに置きます。アプリはルートプロジェクト（`:`）
        なので、定義を外に出してもアプリ側の main ソースセットは1つのままです。

        中身は1宣言1ファイルです。`ProjectArchitecture.kt` が入口、`groups/<Name>Group.kt` が
        グループ、`roles/<Name>Role.kt` が役割、`processors/` がこのサンプル自身が書いた
        プロセッサです。定義どおりかを確かめる `ProjectArchitectureTest` と、katachi 側の
        結合テストである `*Spec` も同じモジュールにあるので、この役割が覆います。

        `layout { }` は `**` でテストソースセット全体を見ています。`groups/` と `roles/` に
        分けるのは読みやすさのための約束であって、katachi の `layout { }` が強制しているわけでは
        ありません（`roles/` に何も宣言しない `.kt` を置いても通ります）。
    """.trimIndent()
    forbiddenContents = """
        - アプリのコード。`:architecture-test` に `src/main/kotlin` を作ると、
          どの役割も覆わないファイルとして落ちます
        - どのレイヤーに属するかが決まっているもの。ここは「形」だけを書く場所です
    """.trimIndent()
    example("ProjectArchitecture.kt", "定義の入口")
    example("roles/ControllerRole.kt", "役割1つの宣言")
    example("ProjectArchitectureTest.kt", "定義を assert するテスト")
    layout {
        // The price of the recommended setup: `:architecture-test` checks itself, so
        // the definition has to give itself a role like everything else. The module
        // path resolves to `architecture-test/`, which is where the files actually are.
        //
        // `**` covers `groups/` and `roles/` without naming them, so splitting the
        // definition further costs no line here — and buys no enforcement either: a file
        // under `roles/` that declares no role passes just the same.
        ":architecture-test".module {
            testSourceSet / kotlin / "**" / "*".ktFile()
        }
    }
}
