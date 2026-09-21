package com.example.kmp.application

import com.example.kmp.modulePackage
import me.tbsten.katachi.dsl.ArchitectureScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

/** The data layer, including the parts that differ per platform. */
fun ArchitectureScope.dataRoles() {
    "data".group {
        title = "データ"

        // `:data` is one module split into packages, the same way `:ui` is.
        // `user` holds the repositories, `platform` the expect/actual pair. There is no
        // `settings` package here: unlike sample/android this sample has no
        // SettingsRepository, and the settings screen reads `:data` through UserRepository
        // and `platformName()`.
        "Repository" {
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
        // The role that only exists because this is a KMP project: `commonMain` declares
        // `expect`, and `androidMain` / `iosMain` supply the `actual`. The three files have
        // to sit in the same package, so the package is part of what this role describes.
        "PlatformImplementation" {
            title = "プラットフォーム実装"
            summary = ":data モジュールの platform package。commonMain の expect 宣言と、" +
                "androidMain / iosMain の actual 実装が同じ package に揃う"
            example("PlatformInfo.kt", "commonMain の expect 宣言")
            example("PlatformInfo.android.kt", "Android 向けの actual")
            example("PlatformInfo.ios.kt", "iOS 向けの actual")
            // The same package under three source sets, and the source set name is now the
            // only part that varies: `"<name>".sourceSet` is `src/<name>` and nothing else,
            // which is exactly what a KMP source set is.
            layout {
                ":data".module {
                    "commonMain".sourceSet / kotlin / modulePackage / "platform" / "*".ktFile()
                    "androidMain".sourceSet / kotlin / modulePackage / "platform" / "*.android".ktFile()
                    "iosMain".sourceSet / kotlin / modulePackage / "platform" / "*.ios".ktFile()
                }
            }
        }
    }
}
