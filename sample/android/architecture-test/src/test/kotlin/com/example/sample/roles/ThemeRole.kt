package com.example.sample.roles

import com.example.sample.modulePackage
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

/** The role of the one place colours, typography and shapes are decided. */
fun DeclarationContainerScope.theme() = "Theme" {
    title = "テーマ"
    summary = ":ui モジュールの theme package に置く、色・タイポグラフィ・形"
    example("AppTheme", "アプリのテーマ")
    layout {
        ":ui".module {
            // Named exactly, not `*.kt`: there is one theme, and a second file turning up
            // here should be a violation rather than a silent second theme.
            mainSourceSet / kotlin / modulePackage / "theme" / "AppTheme".ktFile()
        }
    }
}
