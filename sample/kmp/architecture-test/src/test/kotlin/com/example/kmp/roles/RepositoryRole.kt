package com.example.kmp.roles

import com.example.kmp.forbiddenContents
import com.example.kmp.modulePackage
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.div
import me.tbsten.katachi.dsl.gradle.kotlin
import me.tbsten.katachi.dsl.gradle.module
import me.tbsten.katachi.dsl.gradle.sourceSet
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.dsl.template

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

        layout の宣言を2つに分けてあるのは、インターフェースの名前が実装のファイル名に
        一致しないからです。部分一致の capture も `*` と同じく後ろに続く文字列を越えないので、
        実装を拾うには実装のパターンを書く必要があります。1つのパターンで済ませようとして
        緩めると、宣言していない形まで通ってしまいます。

        インターフェースと実装、それぞれのファイルの宣言に `.template` を1つずつ付けてあります。
        `--arg template=data.Repository.repository,data.Repository.repositoryImpl --arg name=Cache`
        で両方を1回に、`data.Repository.repository` だけを指定すればインターフェースだけを
        作れます（実装は手で書くか、後から `data.Repository.repositoryImpl` で追加できます）。

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
    // Two file declarations, not one: an interface file name does not match an implementation
    // file name, because a capture never crosses what follows it, same as `*`.
    // Each carries its own `.template`, told apart by `id` (`repository` / `repositoryImpl`).
    //   ./gradlew :architecture-test:katachiTemplate --arg template=data.Repository.repository \
    //       --arg name=Cache
    layout {
        ":data".module {
            "commonMain".sourceSet / kotlin / modulePackage / "user" {
                "${capture("name")}Repository".ktFile()
                    .template(id = "repository") {
                        val name = captureValue("name")
                        val item by stringParameter(default = "String")
                        """
                            package com.example.kmp.data.user

                            /** Reads ${name.lowercase()} data. */
                            interface ${name}Repository {
                                fun items(): List<$item>
                            }
                        """.trimIndent() + "\n"
                    }
                "${capture("name")}RepositoryImpl".ktFile()
                    .template(id = "repositoryImpl") {
                        val name = captureValue("name")
                        // Same parameter as the interface's template: give it once and it binds both,
                        // when the two are generated together.
                        val item by stringParameter(default = "String")
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
}
