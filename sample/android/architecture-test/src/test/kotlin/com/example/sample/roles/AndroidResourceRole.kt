package com.example.sample.roles

import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*

/** The role of everything `:app` carries that is not Kotlin. */
fun DeclarationContainerScope.androidResource() = "AndroidResource" {
    title = "Android リソース"
    summary = "AndroidManifest.xml・res/・proguard-rules.pro"
    description = """
        `:app` が持つ、Kotlin ではないファイル。`AndroidManifest.xml`、`res/`、
        `proguard-rules.pro` の3つで、どれも形を決めているのは Android のビルドシステムであって
        このプロジェクトではない。

        `AndroidManifest.xml` と `proguard-rules.pro` は名指し。どちらもアプリに1つしか無いので、
        2つ目が現れたら違反にする。

        `res/` は `ignore()` してある。中の構成（`values/` `drawable-*/` など）は
        Android のリソースシステムが決めた規則で、AGP がすでに検証している。
        katachi 側でもう一度書き下すと、同じ規則の写しが2つできて、片方が必ず古くなる。
        いま入っているのは `values/strings.xml` と `values/themes.xml`。

        リソースを持てるモジュールは `:app` だけではない（`:ui` も Android ライブラリ）が、
        このサンプルでは `:app` 以外に `res/` が無いので、この役割は `:app` だけを見ている。
        他のモジュールにリソースを置くなら、そのとき役割を広げる。
    """.trimIndent()
    example("AndroidManifest.xml", "アプリの構成")
    example("res/values/strings.xml", "文字列リソース")
    layout {
        ":app".module {
            "proguard-rules.pro".file()
            mainSourceSet {
                "AndroidManifest.xml".file()
                // `ignore()` rather than a tree of directories: the shape of `res/`
                // is the Android resource system's, not this project's, and it is
                // already validated by AGP.
                "res".ignore()
            }
        }
    }
}
