package com.example.roles

import com.example.forbiddenContents
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktsFile

/** The role of the build definition and the Gradle wrapper. */
fun DeclarationContainerScope.gradle() = "Gradle" {
    title = "Gradle スクリプト"
    summary = "ビルドの定義と Gradle wrapper"
    description = """
        ビルド時にだけ効くものを集めた役割です。ルートの `build.gradle.kts` と
        `settings.gradle.kts`、`gradle.properties`、wrapper 一式（`gradlew`, `gradlew.bat`,
        `gradle/wrapper/`）、そしてこのサンプル専用の version catalog
        `gradle/sample.versions.toml` が入ります。

        このサンプルでは `architecture-test/build.gradle.kts` がとくに読む価値のある1本です。
        3本の processor を `katachi { processors { register(key, fqcn) } }` で登録し、
        `roleTable` には `arg("sortBy", "Name")` でモジュールの既定値を与えています。

        `:architecture-test` については `.module { }` を空で書いてあります。`.module { }` は
        それ自体がディレクトリと `build.gradle.kts` と `build/` の除外を意味するので、
        モジュールが1つあるという事実だけを書けば足ります。

        この役割が属する `build` グループは `documented = false` なので、生成ドキュメントには
        出ません。チェックの対象からは外れていません。
    """.trimIndent()
    forbiddenContents = """
        - 実行時に読む設定。このサンプルには実行時設定がありませんが、増えたときに
          ビルド設定と同じ場所に置くと、どちらを触れば動きが変わるのかが読めなくなります
        - ビルドが書き出したもの。`build/` と `.kotlin/` は `.gitignore` にあり、既定の
          `files = gitTracked()` では検査に渡りません。それでも各 `.module { }` が
          `"build".ignore()` と書くのは、`files = wholeTree()` を選んだプロジェクトでも
          理由が読めるようにするためです
    """.trimIndent()
    example("architecture-test/build.gradle.kts", "processor 3本の登録")
    example("settings.gradle.kts", "ビルド全体の配線")
    example("gradle/sample.versions.toml", "このサンプル専用の catalog")
    layout {
        // The application module is the root project, so `":"` resolves to the repository
        // root. `build.gradle.kts` is not written out: `.module { }` is exactly a directory
        // plus `"build".ignore()` and `"build.gradle".ktsFile()`.
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
        // The only thing this role has to say about `:architecture-test` is what every module
        // brings with it, so the block is empty.
        ":architecture-test".module { }
    }
}
