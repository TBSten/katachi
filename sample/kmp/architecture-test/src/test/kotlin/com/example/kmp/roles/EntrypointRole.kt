package com.example.kmp.roles

import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

/** Where the Android application starts, and the composable it hands the whole screen to. */
fun DeclarationContainerScope.entrypoint() = "Entrypoint" {
    title = "エントリポイント"
    summary = "Android アプリの起動点。ComponentActivity と、そこから setContent で呼ぶアプリ全体の @Composable"
    example("MainActivity", "起動時に表示される Activity")
    example("AppRoot", "テーマとナビゲーションを組み立てる Composable")
    // `mainSourceSet`, not `"commonMain".sourceSet`: `:app:android` is the Android
    // application module, and its code lives in `src/main` like any Android module's.
    layout {
        ":app:android".module {
            mainSourceSet / kotlin / "com/example/kmp/app" / "*".ktFile()
        }
    }
}
