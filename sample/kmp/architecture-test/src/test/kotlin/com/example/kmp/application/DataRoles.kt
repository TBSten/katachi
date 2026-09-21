package com.example.kmp.application

import me.tbsten.katachi.dsl.ArchitectureScope

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
                "data/src/commonMain/kotlin" {
                    "com/example/kmp/data/user" / "*Repository".ktFile()
                    "com/example/kmp/data/user" / "*RepositoryImpl".ktFile()
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
            // The same package under three source sets. Written out as three directory
            // chains here; from step 3 the source set name is the only part that varies.
            layout {
                "data/src/commonMain/kotlin" {
                    "com/example/kmp/data/platform" / "*".ktFile()
                }
                "data/src/androidMain/kotlin" {
                    "com/example/kmp/data/platform" / "*.android".ktFile()
                }
                "data/src/iosMain/kotlin" {
                    "com/example/kmp/data/platform" / "*.ios".ktFile()
                }
            }
        }
    }
}
