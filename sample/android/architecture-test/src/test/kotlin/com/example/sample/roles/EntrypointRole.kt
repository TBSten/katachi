package com.example.sample.roles

import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

/** The role Android itself reaches for when it starts the app. */
fun DeclarationContainerScope.entrypoint() = "Entrypoint" {
    title = "エントリポイント"
    summary = ":app に置く、Android がアプリを起動するときに触る型"
    description = """
        Android が起動時に最初に触る型。`:app` に `MainActivity` と `MainApplication` が
        1つずつ、名指しで置いてある。どちらかが消えると `[MissingFile]` で検査が落ちる。
        起動できないアプリが検査を通ってしまわないようにするため。

        `MainActivity` は `setContent { AppTheme { AppNavHost() } }` だけを書く。
        `AppNavHost` は同じファイルの private な `@Composable` で、`:feature:*` が公開する
        Route を並べてナビゲーショングラフを組み立てる。すべての feature を知ってよい
        モジュールは `:app` だけで、その知識はこのファイルの中に閉じている。

        `MainApplication` は `Application` を継承するだけ。DI コンテナの初期化のような
        「起動時に1回だけ」の処理を足す場所として空けてある。

        置いてはいけないもの: 画面の中身。`:app` は feature をつなぐだけで、
        UI は `:ui` と `:feature:*` にある。ここに Composable が増え始めたら、
        それは feature モジュールに引っ越すべきもの。

        この役割の package は `modulePackage` を使わず `com/example/sample` と直接書く。
        `:app` はアプリ本体で、`:ui` → `com.example.sample.ui` のような
        モジュールパスとの対応を持たないため。
    """.trimIndent()
    example("MainActivity", "起動時に表示される Activity")
    example("MainApplication", "Application の実装")
    layout {
        ":app".module {
            // Both named exactly, so an app that loses its entry point fails with
            // `[MissingFile]` instead of quietly passing.
            mainSourceSet / kotlin / "com/example/sample" {
                "MainActivity".ktFile()
                "MainApplication".ktFile()
            }
        }
    }
}
