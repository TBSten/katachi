package com.example.sample.roles

import com.example.sample.forbiddenContents
import com.example.sample.groups.DataDomain
import com.example.sample.modulePackage
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.dsl.template

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

        `User*Repository.kt` / `User*RepositoryImpl.kt` の部分だけテンプレートから生成できる
        （`UserRepository.kt` / `UserRepositoryImpl.kt` 自身は各領域に最初から手書きで置いてあり、
        生成の対象ではない）。領域ごとに id を分けてあり、`--arg
        template=data.Repository.user,data.Repository.userImpl --arg name=Cache` で両方を1回に作る。
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
    //
    // The domain's own file (`UserRepository.kt`, no capture) is written by hand and carries no
    // template. The capturing pattern's `.template` is attached inside the DataDomain loop, with
    // an id made from the loop variable (`user` / `userImpl`, `settings` / `settingsImpl`): one
    // source line run once per domain is still one template per domain, as long as each run
    // passes its own id.
    //   ./gradlew :architecture-test:katachiTemplate \
    //       --arg template=data.Repository.user --arg name=Profile
    layout {
        ":data".module {
            mainSourceSet / kotlin / modulePackage {
                description = "インターフェース。呼び出し側が依存する型"
                DataDomain.entries.forEach { domain ->
                    domain.packageName {
                        "${domain.name}Repository".ktFile()
                        "${domain.name}${capture("name")}Repository".ktFile()
                            .template(id = domain.templateId) { repositoryInterfaceContent(domain, captureValue("name")) }
                    }
                }
            }
        }
    }
    layout {
        ":data".module {
            mainSourceSet / kotlin / modulePackage {
                description = "実装。インターフェースと同じ package に並べる"
                // Each id shares its interface template's `name` capture: passing it once binds
                // both, when the two are generated together.
                DataDomain.entries.forEach { domain ->
                    domain.packageName {
                        "${domain.name}RepositoryImpl".ktFile()
                        "${domain.name}${capture("name")}RepositoryImpl".ktFile()
                            .template(id = "${domain.templateId}Impl") {
                                repositoryImplContent(domain, captureValue("name"))
                            }
                    }
                }
            }
        }
    }
}

/** The id a [DataDomain]'s interface template answers to: `user` for [DataDomain.User]. */
private val DataDomain.templateId: String get() = name.replaceFirstChar(Char::lowercaseChar)

/** The interface [repository]'s `.template { }` writes, for one [DataDomain]. */
private fun repositoryInterfaceContent(domain: DataDomain, name: String): String {
    val repository = "${domain.name}${name}Repository"
    val packageName = "com.example.sample.data.${domain.packageName}"
    return """
        package $packageName

        /** Reads ${name.lowercase()} data. */
        interface $repository {
            // TODO: replace with what this repository actually reads and writes.
            fun load(): String
        }
    """.trimIndent() + "\n"
}

/** The implementation [repository]'s `.template { }` writes, for one [DataDomain]. */
private fun repositoryImplContent(domain: DataDomain, name: String): String {
    val repository = "${domain.name}${name}Repository"
    val packageName = "com.example.sample.data.${domain.packageName}"
    return """
        package $packageName

        /** Production implementation of [$repository]. */
        class ${repository}Impl : $repository {
            override fun load(): String = ""
        }
    """.trimIndent() + "\n"
}
