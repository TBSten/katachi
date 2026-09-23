package com.example.kmp.roles

import com.example.kmp.modulePackage
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

/** The `MaterialTheme` setup and the design tokens around it: the `theme` package of `:ui`. */
fun DeclarationContainerScope.theme() = "Theme" {
    title = "テーマ"
    summary = ":ui モジュールの theme package。MaterialTheme の設定と、色・余白のデザイントークン"
    example("AppTheme", "アプリ全体のテーマ")
    example("AppSpacing", "余白のトークン")
    layout {
        ":ui".module {
            "commonMain".sourceSet / kotlin / modulePackage / "theme" / "*".ktFile()
        }
    }
}
