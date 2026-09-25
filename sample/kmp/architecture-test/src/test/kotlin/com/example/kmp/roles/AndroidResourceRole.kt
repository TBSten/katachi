package com.example.kmp.roles

import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*

/** What the Android build needs beside the Kotlin sources of `:app:android`. */
fun DeclarationContainerScope.androidResource() = "AndroidResource" {
    title = "Android リソース"
    summary = "AndroidManifest.xml と res/ 以下のリソース XML。:app:android だけが持つ"
    description = """
        Kotlin のソース以外で Android のビルドが要求するものです。`AndroidManifest.xml` と
        `res/` 以下のリソース XML で、このサンプルでは `:app:android` にしかありません。

        `res/*/` の `*` はリソース修飾子のディレクトリ（`values`、`drawable`、`mipmap-hdpi` …）
        です。どんな名前がありうるかを決めるのは Android で、このプロジェクトではないので、
        階層だけを宣言して個々のディレクトリ名は書いていません。

        置いてはいけないもの:

        - Kotlin のコード。`src/main/kotlin` 側は Entrypoint の役割です
        - iOS 側のリソース。`app/ios` の中身は Xcode の領分です
        - 画面に出す文言。KMP では iOS から `res/` が読めないので、共有したい文字列を
          `strings.xml` に書くと Android でしか使えないものになります。実際このサンプルでは
          遷移先のラベルもボタンの文字も Compose 側（`commonMain`）に直接書いていて、
          `strings.xml` にあるのはアプリ名だけです

        ライブラリモジュール（`:ui` など）はリソースを持ちません。色も余白も `:ui` の theme
        package に Kotlin で置いてあり、`res/values/` に相当するものがありません。Android 専用の
        置き場を使わないことが、そのまま iOS と共有できることになります。
    """.trimIndent()
    example("AndroidManifest.xml", "アプリのマニフェスト")
    example("res/values/strings.xml", "文字列リソース")
    // `res/*/` is the resource qualifier directory (`values`, `drawable`,
    // `mipmap-hdpi`, ...). Android decides those names, so the layout names the
    // level rather than each directory.
    layout {
        ":app:android".module {
            mainSourceSet / "AndroidManifest.xml".file()
            mainSourceSet / "res" / "*" / "*.xml".file()
        }
    }
}
