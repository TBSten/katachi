package com.example.kmp.groups

import com.example.kmp.roles.androidResource
import com.example.kmp.roles.entrypoint
import com.example.kmp.roles.xcodeProject
import me.tbsten.katachi.dsl.DeclarationContainerScope

/**
 * The two applications: the Android one Gradle builds, and the iOS one Xcode builds.
 *
 * `:app:android` is the one nested module path of this sample, and it is also the one module
 * whose package does not follow it: the sources sit in `com.example.kmp.app`, not in
 * `com.example.kmp.app.android`. So the package is written out in the roles below instead of
 * coming from `modulePackage` — a module that does not follow the rule should say so rather
 * than bend it.
 */
fun DeclarationContainerScope.appGroup() = "app".group {
    title = "アプリ"
    summary = "Gradle がビルドする Android アプリと、Xcode がビルドする iOS アプリ"
    description = """
        実際に配布される2つのアプリです。どちらも「アプリ」なのに、書き方がまるで違うので
        1つの group にまとめました。

        `:app:android` は Gradle モジュールなのでモジュールパスで書けます。`app/ios` は
        `settings.gradle.kts` が意図的に include していないので、モジュールパスが存在せず、
        ただのディレクトリキーとして宣言して `ignore()` で検査を止めます。現実の KMP
        リポジトリは Gradle が管理するディレクトリと管理しないディレクトリが混ざるので、
        その両方を書けることを見せるのがこの group です。

        `:app:android` にはもう1つ特徴があります。このサンプル唯一の入れ子のモジュールパスで、
        かつ唯一 package がモジュールパスに従わないモジュール（`com.example.kmp.app`、
        `com.example.kmp.app.android` ではない）です。だから Entrypoint と AndroidResource は
        `modulePackage` を使わず package を直書きしています。規則に従わないものを規則で
        書こうとして曲げるより、違うと書く方が読み手に親切です。

        置いてよいもの:

        - 起動点と、アプリ全体の組み立て（`AppRoot`）
        - Android のビルドが要求するリソース

        置いてはいけないもの:

        - 画面そのもの。画面は feature モジュールにあり、ここは Route を呼ぶだけです
        - 共有したいロジック。ここに書いたものは iOS から見えません
    """.trimIndent()

    entrypoint()
    androidResource()
    xcodeProject()
}
