package com.example.sample.roles

import com.example.sample.forbiddenContents
import com.example.sample.groups.DataDomain
import com.example.sample.modulePackage
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

/** The role that fetches and stores data, declared as an interface and its implementation. */
fun DeclarationContainerScope.repository() = "Repository" {
    title = "リポジトリ"
    summary = "データの取得と保存。インターフェースと実装を :data の、扱う対象ごとの package " +
        "（user / settings）に並べて置く"
    description = """
        データの取得と保存を引き受ける、`:data` の唯一の役割。扱う対象ごとに package を割り
        （`user` / `settings`）、その中にインターフェース（`*Repository.kt`）と実装
        （`*RepositoryImpl.kt`）を並べる。ファイル名は package の名前で始める（`user` なら
        `User*Repository.kt`）。呼び出し側が依存するのはインターフェースだけで、
        `Impl` の名前を ViewModel の引数に書くことはない。

        この役割は `layout { }` を2つ持つ。置き場所は同じ package で、違うのはファイル名の
        パターンだけなので、どちらがインターフェースでどちらが実装かは `layout` の
        `description` が説明する。1つの役割が複数の置き場所を持てることの実例でもある。

        `:data` は他のモジュールに依存しない、このアプリで一番下の層。Android にも Compose にも
        触らないので、インターフェースは素の Kotlin として読める。ViewModel はコンストラクタで
        インターフェースを受け取り、テストでは `:testing` の `Fake*` に差し替える。
    """.trimIndent()
    forbiddenContents = """
        - 画面向けの型。`UiState` に詰め替えるのは ViewModel の仕事で、`:data` は `:ui` を知らない
        - `*Repository.kt` `*RepositoryImpl.kt` 以外のファイル。DTO やデータソースを
          分けたくなったら、まず役割を増やす
        - 対象をまたぐ package。`user` の型が `settings` に混ざったら package を割り直す
    """.trimIndent()
    example("UserRepository", "ユーザーの取得と保存のインターフェース")
    example("UserRepositoryImpl", "UserRepository の実装")
    // Two layouts: a role may live in more than one place. Here the interface
    // (`*Repository.kt`) and the implementation (`*RepositoryImpl.kt`). Both sit in
    // the same package, so `description` is what tells the two apart — which is the
    // question it exists to answer.
    //
    // One package per DataDomain, and a file name has to start with that domain's name: a
    // directory `capture(...)` has no value the layout could read back to write that requirement
    // once, so it is written out per domain instead (see DataDomain's KDoc). `capture(...)`
    // would still be enough to just choose which package a generated file goes in.
    // Written twice because katachi's `*` matches one character or more, so
    // `User*Repository` alone would not accept `UserRepository` itself.
    layout {
        ":data".module {
            mainSourceSet / kotlin / modulePackage {
                description = "インターフェース。呼び出し側が依存する型"
                DataDomain.entries.forEach { domain ->
                    domain.packageName {
                        "${domain.name}Repository".ktFile()
                        "${domain.name}*Repository".ktFile()
                    }
                }
            }
        }
    }
    layout {
        ":data".module {
            mainSourceSet / kotlin / modulePackage {
                description = "実装。インターフェースと同じ package に並べる"
                DataDomain.entries.forEach { domain ->
                    domain.packageName {
                        "${domain.name}RepositoryImpl".ktFile()
                        "${domain.name}*RepositoryImpl".ktFile()
                    }
                }
            }
        }
    }
    // The interface declares one member, `load()`, which the Fake template implements
    // too: generate both with the same `domain` and `name` and they fit each other.
    //   ./gradlew :architecture-test:katachiTemplate \
    //       --arg roleName=Repository --arg domain=User --arg name=Profile
    template {
        val domain by enumParameter(DataDomain.entries)
        val name by stringParameter()
        val withImpl by booleanParameter(default = true)
        val repository = "${domain.name}${name}Repository"
        val packageName = "com.example.sample.data.${domain.packageName}"

        file("$repository.kt") {
            """
                package $packageName

                /** Reads ${name.ifEmpty { domain.name }.lowercase()} data. */
                interface $repository {
                    // TODO: replace with what this repository actually reads and writes.
                    fun load(): String
                }
            """.trimIndent()
        }
        if (withImpl) {
            file("${repository}Impl.kt") {
                """
                    package $packageName

                    /** Production implementation of [$repository]. */
                    class ${repository}Impl : $repository {
                        override fun load(): String = ""
                    }
                """.trimIndent()
            }
        }
    }
}
