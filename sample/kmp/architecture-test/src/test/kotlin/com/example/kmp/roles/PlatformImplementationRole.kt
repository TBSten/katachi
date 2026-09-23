package com.example.kmp.roles

import com.example.kmp.modulePackage
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

/**
 * The role that only exists because this is a KMP project.
 *
 * `commonMain` declares `expect`, and `androidMain` / `iosMain` supply the `actual`. The three
 * files have to sit in the same package, so the package is part of what this role describes.
 */
fun DeclarationContainerScope.platformImplementation() = "PlatformImplementation" {
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
