package com.example.sample.roles

import com.example.sample.modulePackage
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

/** The role of the types the UI layer itself is built out of. */
fun DeclarationContainerScope.uiCore() = "UiCore" {
    title = "UI 基盤"
    summary = ":ui モジュールの core package に置く、UI 層の土台になる型"
    example("UiState", "画面状態を表す sealed interface")
    layout {
        ":ui".module {
            mainSourceSet / kotlin / modulePackage / "core" / "*".ktFile()
        }
    }
}
