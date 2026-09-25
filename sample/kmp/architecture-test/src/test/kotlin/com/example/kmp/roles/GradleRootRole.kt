package com.example.kmp.roles

import com.example.kmp.processor.owner
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.kotlin.ktsFile

/** The build files that sit around the modules rather than inside one of them. */
fun DeclarationContainerScope.gradleRoot() = "GradleRoot" {
    title = "ルートのビルドファイル"
    summary = "settings.gradle.kts、ルートの build.gradle.kts、gradle.properties、wrapper"
    documented = false
    owner = "platform"
    description = """
        モジュールの中ではなく、モジュールの周りにあるビルドファイルです。`layout { }` の直下に
        書いたパスはプロジェクトルートからの相対になります。このサンプルのプロジェクトルートは
        `sample/kmp`、つまりテストの作業ディレクトリから上に辿って最初に `gradlew` を持つ
        ディレクトリです（`ProjectRootSpec` がそれを確かめています）。

        ルートプロジェクトも Gradle のモジュールではありますが、`":".module { }` とは書いて
        いません。module ブロックの container はそこではプロジェクトルートそのものになり、
        その `build/` の行がリポジトリ全体の検査を止めてしまうからです。この役割が言いたいのは
        「ビルドの周りのファイル」であって、モジュールではありません。

        version catalog が2つあるのは意図的です。`libs` はリポジトリ root の
        `gradle/libs.versions.toml` で、katachi 本体と3つのサンプルが共有する版の一次情報です。
        このサンプルだけが必要とするもの（AGP）は `gradle/sample.versions.toml` に分けてあります。
        こちらを `gradle/libs.versions.toml` という名前にすると、Gradle が自動で `libs`
        アクセサを作って root の catalog とぶつかります。

        置いてはいけないもの:

        - モジュールの `build.gradle.kts`。それは GradleModule の役割です
        - `local.properties`。`.gitignore` に入っていて `gitTracked()` に乗らないので、
          そもそもどの役割も宣言する必要がありません
    """.trimIndent()
    example("settings.gradle.kts", "モジュール構成と catalog の宣言")
    example("gradle/sample.versions.toml", "このサンプル用の catalog")
    // Directly under `layout { }` the paths are relative to the project root, which
    // for this sample is `sample/kmp` -- the first directory above the test's working
    // directory holding a `gradlew` (see ProjectRootSpec).
    //
    // Not written as `":".module { }` even though the root project is a Gradle module
    // too: a module block's container is the project root itself there, so its
    // `build/` line would stop the check over the whole repository. What this role
    // describes is the files around the build, not a module.
    layout {
        "settings.gradle".ktsFile()
        "build.gradle".ktsFile()
        "gradle.properties".file()
        "gradlew".file()
        "gradlew.bat".file()
        "gradle" {
            // Not `libs.versions.toml`: this sample reads the repository root catalog
            // as `libs` and keeps only what it adds on top in a file of its own.
            "sample.versions.toml".file()
            "wrapper" {
                "gradle-wrapper.jar".file()
                "gradle-wrapper.properties".file()
            }
        }
    }
}
