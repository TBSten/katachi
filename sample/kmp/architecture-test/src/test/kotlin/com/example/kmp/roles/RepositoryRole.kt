package com.example.kmp.roles

import com.example.kmp.forbiddenContents
import com.example.kmp.modulePackage
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.div
import me.tbsten.katachi.dsl.gradle.kotlin
import me.tbsten.katachi.dsl.gradle.module
import me.tbsten.katachi.dsl.gradle.sourceSet
import me.tbsten.katachi.dsl.kotlin.ktFile

/**
 * The way into the data of the app: the `user` package of `:data`.
 *
 * There is no `settings` package beside it. Unlike sample/android this sample has no
 * SettingsRepository, and the settings screen reads `:data` through UserRepository and
 * `platformName()`.
 */
fun DeclarationContainerScope.repository() = "Repository" {
    title = "リポジトリ"
    summary = ":data モジュールの user package。データの取得口で、" +
            "インターフェースと実装の2つの置き方を持つ"
    description = """
        アプリがデータに触る入口です。`:data` の `user` package に、インターフェース
        （`*Repository.kt`）と実装（`*RepositoryImpl.kt`）を並べて置きます。呼ぶ側
        （ViewModel）が依存するのはインターフェースの方だけです。

        layout のパターンを2行に分けてあるのは、`*Repository.kt` が
        `UserRepositoryImpl.kt` に一致しないからです。`*` は後ろに続く文字列を越えないので、
        実装を拾うには実装のパターンを書く必要があります。1行で済ませようとして
        `*Repository*.kt` と緩めると、`UserRepositoryFactory.kt` のような宣言していない形まで
        通ってしまいます。

        `commonMain` だけを宣言しています。プラットフォームで実装が変わるものは、この役割では
        なく隣の PlatformImplementation（expect/actual）が引き受けます。ここに `androidMain` を
        足すと、同じ「プラットフォーム差の吸収」が2か所に散ります。

        sample/android と違い、このサンプルには設定用のリポジトリがありません。設定画面は
        `UserRepository` と `platformName()` を読むだけで足りています。使われていない package を
        定義に書くと、実体の無いディレクトリをドキュメントが案内することになります。
    """.trimIndent()
    forbiddenContents = """
        - UI の型。`UiState` は `:ui` の core package にあり、`:data` はそれを知りません
        - テスト用の偽実装。`FakeUserRepository` は `:testing` の Fake の役割です
    """.trimIndent()
    example("UserRepository", "ユーザーを取得するインターフェース")
    example("UserRepositoryImpl", "UserRepository の実装")
    // Two file patterns, not one: `*Repository.kt` does not match
    // `UserRepositoryImpl.kt`, because `*` never crosses what follows it.
    layout {
        ":data".module {
            "commonMain".sourceSet / kotlin / modulePackage / "user" / "*Repository".ktFile()
            "commonMain".sourceSet / kotlin / modulePackage / "user" / "*RepositoryImpl".ktFile()
        }
    }

    template {
        val name by stringParameter()
        val item by stringParameter(default = "String")
        // --arg withImpl=false produces the interface alone, for an implementation written by hand.
        val withImpl by booleanParameter(default = true)

        file("${name}Repository.kt") {
            """
                package com.example.kmp.data.user

                /** Reads ${name.lowercase()} data. */
                interface ${name}Repository {
                    fun items(): List<$item>
                }
            """.trimIndent() + "\n"
        }
        if (withImpl) {
            file("${name}RepositoryImpl.kt") {
                """
                    package com.example.kmp.data.user

                    /** The real implementation. A stub, like [UserRepositoryImpl]. */
                    class ${name}RepositoryImpl : ${name}Repository {
                        override fun items(): List<$item> = emptyList()
                    }
                """.trimIndent() + "\n"
            }
        }
    }
}
