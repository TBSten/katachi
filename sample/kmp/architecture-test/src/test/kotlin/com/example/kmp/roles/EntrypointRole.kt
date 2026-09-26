package com.example.kmp.roles

import com.example.kmp.allowedContents
import com.example.kmp.forbiddenContents
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

/** Where the Android application starts, and the composable it hands the whole screen to. */
fun DeclarationContainerScope.entrypoint() = "Entrypoint" {
    title = "エントリポイント"
    summary = "Android アプリの起動点。ComponentActivity と、そこから setContent で呼ぶアプリ全体の @Composable"
    description = """
        Android アプリが動き出す場所です。`:app:android` の `src/main` に置きます。
        KMP モジュールの `commonMain` ではありません。`:app:android` は Android の application
        モジュールなので、ソースの置き場は他の Android モジュールと同じ `src/main` です。

        中身は2段に分かれています。`MainActivity` は配線だけ、つまり `UserRepositoryImpl` を
        作って `setContent { AppRoot(...) }` を呼ぶだけです。テーマ・ナビゲーションバー・
        現在の遷移先に対応する Route の組み立ては `AppRoot` が持ちます。分けてあるのは、
        `app/ios` が `ComposeUIViewController` を持つようになったときに共有できるのが
        `AppRoot` 側だからです。Activity は Android 固有のまま残ります。

        このモジュールの package は `com.example.kmp.app` で、モジュールパス `:app:android` からは
        導けません。だから layout は `modulePackage` を使わず package を直書きしています。
        規則に従わないものは、従わないと書く方が正直です。
    """.trimIndent()
    allowedContents = """
        - プラットフォームの起動点（`ComponentActivity`）と、その最小限の配線
        - アプリ全体を組み立てる `@Composable`
    """.trimIndent()
    forbiddenContents = """
        - 画面そのもの。画面は feature モジュールにあり、ここは Route を呼ぶだけです
        - 状態。画面の状態は ViewModel、現在地は `:navigation` の `Navigator` が持ちます
        - 共有できる部品。ここに書いたものは iOS から見えません
    """.trimIndent()
    example("MainActivity", "起動時に表示される Activity")
    example("AppRoot", "アプリ全体を組み立てる Composable")
    // `mainSourceSet`, not `"commonMain".sourceSet`: `:app:android` is the Android
    // application module, and its code lives in `src/main` like any Android module's.
    layout {
        ":app:android".module {
            mainSourceSet / kotlin / "com/example/kmp/app" / "*".ktFile()
        }
    }
}
