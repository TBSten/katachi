package com.example.kmp.roles

import com.example.kmp.forbiddenContents
import com.example.kmp.modulePackage
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.dsl.pascalCase

/**
 * What binds a screen to a navigation destination, named after its feature module.
 *
 * The third of the three files a feature module is required to hold, and the third place
 * `wildcard("feature")` ties a file name to the module it sits in.
 */
fun DeclarationContainerScope.route() = "Route" {
    title = "ルート"
    summary = "画面を navigation の Destination に結びつけ、ViewModel の生成も引き受ける"
    description = """
        feature モジュールの外から触れる唯一の入口です。`object HomeRoute` が
        `destination`（この画面がどの `Destination` なのか）と `Content()`（描画の呼び出し口）を
        持ち、呼ぶ側はその2つしか知りません。

        ViewModel の生成をここが引き受けているのが要点です。`Content()` の中で
        `viewModel { HomeViewModel(repository) }` を呼ぶので、`:app:android` の AppRoot は
        HomeViewModel という型の存在を知らずに画面を出せます。依存（今は UserRepository）は
        引数で受け取ります。このサンプルは DI コンテナを持たず、手渡しで済ませています。

        `commonMain` 固定にしてあるのは、Android の `AppRoot` からも、将来 `app/ios` が
        `ComposeUIViewController` を持ったときにも、同じ Route を呼べるようにするためです。
    """.trimIndent()
    forbiddenContents = """
        - 画面の中身。Compose のツリーを組むのは Screen です
        - 遷移先そのものの定義。`Destination` は `:navigation` にあり、Route はそれを指すだけです
        - 遷移の制御。今どこにいるかを持つのは `:navigation` の `Navigator` です
    """.trimIndent()
    example("HomeRoute", "ホーム画面の遷移先")
    example("SettingsRoute", "設定画面の遷移先")
    layout {
        ":feature:${capture("feature")}".module {
            "commonMain".sourceSet / kotlin / modulePackage /
                "${wildcard("feature").pascalCase}Route".ktFile()
        }
    }
}
