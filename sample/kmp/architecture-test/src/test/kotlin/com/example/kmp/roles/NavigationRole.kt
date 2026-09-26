package com.example.kmp.roles

import com.example.kmp.allowedContents
import com.example.kmp.forbiddenContents
import com.example.kmp.modulePackage
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

/** The whole of the `:navigation` module: where a screen can be reached from, and from where. */
fun DeclarationContainerScope.navigation() = "Navigation" {
    title = "ナビゲーション"
    summary = "遷移先の定義と、現在地を持つ Navigator"
    description = """
        `:navigation` モジュール丸ごとで1つの役割です。package を切っていないので、
        `commonMain` のモジュール package 直下がそのまま範囲になります。`Destination` が
        遷移先の一覧、`Navigator` が現在地を `StateFlow` で持ちます。

        Compose に依存しません。ビルドスクリプトに Compose プラグインを入れていないので、
        ここに `@Composable` を書くとコンパイルが通りません。遷移先をどう見せるか
        （ナビゲーションバーの並べ方）は `:app:android` の `AppRoot` の仕事で、この役割は
        「どこがあるか」と「今どこか」だけを持ちます。

        ナビゲーションライブラリを入れず自前で持っているのは意図的です。ライブラリを入れると
        グラフの定義という置き場所がもう1つ増えて、このサンプルが見せたい「どこに何を置くか」が
        ぼやけます。
    """.trimIndent()
    allowedContents = """
        - 遷移先の型と、その一覧（`Destination.topLevel`）や route 文字列からの引き当て
        - 現在地を持ち、変える手段
    """.trimIndent()
    forbiddenContents = """
        - `@Composable` と画面そのもの
        - feature モジュールへの依存。依存は逆向きで、各 feature の Route が `:navigation` を
          読みます。ここが feature を知ると、画面を1つ足すたびにこのモジュールが太ります
    """.trimIndent()
    example("Destination", "遷移先の一覧")
    example("Navigator", "現在の遷移先を持つ型")
    layout {
        ":navigation".module {
            "commonMain".sourceSet / kotlin / modulePackage / "*".ktFile()
        }
    }
}
