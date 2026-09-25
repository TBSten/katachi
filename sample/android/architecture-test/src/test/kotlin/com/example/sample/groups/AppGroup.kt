package com.example.sample.groups

import com.example.sample.roles.androidResource
import com.example.sample.roles.entrypoint
import me.tbsten.katachi.dsl.DeclarationContainerScope

/**
 * Roles of the application module itself: what `:app` holds beyond wiring the features
 * together.
 *
 * `:app` is the one module of the app whose package is not derived from its module path:
 * its sources sit directly in `com.example.sample`, so the package is written out as a key
 * instead of with `modulePackage`. Everything else about the module — where it is, that it
 * has a build script, that its `build/` is not checked — still comes from `":app".module`.
 */
fun DeclarationContainerScope.appGroup() = "app".group {
    title = "エントリーポイントレイヤー"
    summary = ":app が持つもの。起動の入口と、Android のリソース"
    description = """
        `:app` モジュールそのもの。このアプリで唯一、すべての feature を知ってよいモジュールで、
        `MainActivity` の中でナビゲーショングラフを組み立てて `:feature:home` と
        `:feature:settings` をつなぐ。

        集めたのは、`:app` にしか置けない2つ。Android が起動時に触る型
        （`MainActivity` / `MainApplication`）と、アプリとして必要な Kotlin 以外のファイル
        （`AndroidManifest.xml` / `res/` / `proguard-rules.pro`）。どちらも
        「アプリケーションであること」から来るもので、アプリの機能そのものではない。

        画面の中身はここには無い。`:app` は依存の一番上にいて下向きにしか参照しないので、
        `:app` を丸ごと差し替えても下のモジュールは壊れない。ここに Composable が増え始めたら、
        それは feature モジュールへ引っ越すべきもの。

        `:app` は package がモジュールパスから導けない2つのモジュールのうちの1つで、
        `com/example/sample` を直接書く。アプリ本体なので、`:ui` → `com.example.sample.ui` の
        ような対応を持たないため（もう1つは `:architecture-test`）。
    """.trimIndent()

    entrypoint()
    androidResource()
}
