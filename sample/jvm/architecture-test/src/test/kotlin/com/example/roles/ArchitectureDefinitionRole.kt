package com.example.roles

import com.example.forbiddenContents
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.FileConstraintRange.DirectOnly
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.konsist.konsist

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

        `layout { }` は `com/example` の下を `**` でまるごと見ています。`groups/` と `roles/` に
        分けるのは読みやすさのための約束であって、`layout { }` が強制しているわけではありません
        （`roles/` に何も宣言しない `.kt` を置いても通ります）。

        そのかわり、逆向きの約束だけは `konsist(scope = DirectOnly)` で検査しています。
        `com/example` の**直下**には group・役割の宣言（`DeclarationContainerScope` の拡張関数）を
        置かず、それは `groups/` と `roles/` に書きます。`scope = DirectOnly` なので、この制約は
        `groups/` と `roles/` の中のファイルには降りません。
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
            testSourceSet / kotlin / "com/example" {
                // The samples' one `DirectOnly` constraint (katachi's guide: "Konsist
                // integration"). With the default scope, `Subtree`, it would also cover `groups/` and
                // `roles/`, where every file is exactly such a declaration, and fail.
                "直下に group・役割の宣言を置かない".konsist(scope = DirectOnly) {
                    functions().mustNot { it.receiverType?.name == "DeclarationContainerScope" }
                }
                "*".ktFile()
                "**" / "*".ktFile()
            }
        }
    }
}
