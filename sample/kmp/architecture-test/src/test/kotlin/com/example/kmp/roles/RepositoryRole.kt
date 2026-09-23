package com.example.kmp.roles

import com.example.kmp.modulePackage
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
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
}
