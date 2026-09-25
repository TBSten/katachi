package com.example.sample.roles

import com.example.sample.modulePackage
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

/** The role of moving between screens, which belongs to no single feature. */
fun DeclarationContainerScope.navigation() = "Navigation" {
    title = "画面遷移"
    summary = ":navigation に置く、画面間の移動"
    description = """
        画面を移るための窓口。`AppNavigator` インターフェースが `navigateTo(destination)` と
        `navigateUp()` の2つだけを公開し、`rememberAppNavigator(navController)` が
        `NavHostController` を包んだ実装を返す。実装クラスは private で、外からは名前も見えない。

        `:feature:*` はこのインターフェースにだけ依存する。feature が `NavHostController` を
        直接持てると、どの feature からでもグラフ全体を書き換えられてしまうので、
        触れる範囲をこの2つのメソッドに絞ってある。`:ui` とは別モジュールなのも
        同じ理由で、画面部品を使いたいだけのコードにナビゲーションの依存を持ち込ませない。

        グラフそのものはここには無い。どの Route をどう並べるかを知っているのは `:app` の
        `AppNavHost` だけで、`:navigation` が持つのは「移る手段」に限られる。

        ファイル名は `*.kt`（モジュール直下の package）。遷移の手段に関わる型が増えるなら
        ここに足すが、特定の画面の遷移先（`HomeRoute` など）は feature 側の Route 役割に置く。
    """.trimIndent()
    example("AppNavigator", "画面遷移の窓口")
    layout {
        ":navigation".module {
            mainSourceSet / kotlin / modulePackage / "*".ktFile()
        }
    }
}
