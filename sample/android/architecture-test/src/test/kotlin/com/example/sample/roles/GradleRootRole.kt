package com.example.sample.roles

import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktsFile

/** The role of the build's root: what makes the modules above into one Gradle build. */
fun DeclarationContainerScope.gradleRoot() = "GradleRoot" {
    title = "ルートのビルドスクリプト"
    summary = "settings.gradle.kts・ルートの build.gradle.kts・gradle.properties・wrapper"
    documented = false
    description = """
        バラバラのモジュールを1つの Gradle ビルドにまとめているファイル。
        `settings.gradle.kts`、ルートの `build.gradle.kts`、`gradle.properties`、
        wrapper（`gradlew` / `gradlew.bat` / `gradle/wrapper/`）、そしてバージョンカタログ
        `gradle/sample.versions.toml`。

        `":"` はルートプロジェクトを指す。ルートの `build.gradle.kts` と `build/` は
        他のモジュールとまったく同じ `.module { }` の糖衣から出てくるので、
        `layout` に書き出してあるのはルートにしか無いものだけ。

        `sample/android` は独立した Gradle ビルドで、自分の wrapper と `settings.gradle.kts` を
        持つ。katachi を外から使うプロジェクトと同じ形にするためで、リポジトリのルートとは
        別のビルドになっている。katachi 自身は `includeBuild("../..")` で取り込む。

        カタログが `libs.versions.toml` ではなく `sample.versions.toml` なのは、
        Kotlin・katachi・kotest のバージョンをリポジトリルートのカタログ（`libs`）から
        受け取っているから。名前が重なると Gradle が2つ目の `libs` を勝手に作ってしまう。
        このサンプルだけが要るもの（AGP）がこちらに入る。

        `documented = false`。ビルド設定はこのアプリについて何も語らない。
    """.trimIndent()
    example("settings.gradle.kts", "モジュールの include")
    example("gradle/sample.versions.toml", "サンプル用のカタログ")
    layout {
        // `":"` is the root project, which resolves to the project root itself, so
        // its build script and its `build/` come from the same sugar as every other
        // module's and only what is peculiar to the root is written out.
        ":".module {
            "settings.gradle".ktsFile()
            "gradle.properties".file()
            "gradlew".file()
            "gradlew.bat".file()
            "gradle" {
                "sample.versions.toml".file()
                "wrapper" {
                    "gradle-wrapper.jar".file()
                    "gradle-wrapper.properties".file()
                }
            }
        }
    }
}
