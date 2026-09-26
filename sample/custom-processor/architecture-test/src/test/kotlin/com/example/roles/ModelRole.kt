package com.example.roles

import com.example.allowedContents
import com.example.forbiddenContents
import com.example.modulePackage
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

/** The role of the values the application is about. */
fun DeclarationContainerScope.model() = "Model" {
    title = "モデル"
    summary = "アプリが扱う値。data class・enum・値オブジェクトを置く"
    description = """
        アプリが扱う値そのものです。`Note` は `title` と `body` を持つ data class で、
        `NoteStore` が作り、`main()` がそのまま出力します。

        名前は「何を表す値か」そのもので、接尾辞は付けません（`NoteModel` ではなく `Note`）。
        対象は `model` パッケージ直下の `.kt` だけで、その下にディレクトリを掘っても
        この役割には入りません。
    """.trimIndent()
    allowedContents = "置いてよいのは data class・enum・値オブジェクトと、その値に閉じた計算です。"
    forbiddenContents = """
        - 取得や保存。I/O は保管庫の役割です。モデルが保存先を知ると、値を1つ足すだけで
          保存の話まで読まないといけなくなります
        - 外部ライブラリへの依存。このサンプルのモデルは Kotlin の標準ライブラリしか知りません
    """.trimIndent()
    example("Note", "見出しと本文を持つノート")
    layout {
        // A model is named after the thing it models, so the package is the only marker.
        // Any `.kt` directly in it counts; a subdirectory does not.
        ":".module {
            mainSourceSet / kotlin / modulePackage / "model" / "*".ktFile()
        }
    }
}
