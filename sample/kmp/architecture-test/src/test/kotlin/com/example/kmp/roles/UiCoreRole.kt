package com.example.kmp.roles

import com.example.kmp.modulePackage
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

/** The UI foundation no screen owns: the `core` package of `:ui`. */
fun DeclarationContainerScope.uiCore() = "UiCore" {
    title = "UI 基盤"
    summary = ":ui モジュールの core package。画面に依存しない UI の土台。UiState など"
    example("UiState", "画面の状態を表す型")
    layout {
        ":ui".module {
            "commonMain".sourceSet / kotlin / modulePackage / "core" / "*".ktFile()
        }
    }
}
