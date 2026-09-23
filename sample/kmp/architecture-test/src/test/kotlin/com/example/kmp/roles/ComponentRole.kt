package com.example.kmp.roles

import com.example.kmp.modulePackage
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

/** The shared `@Composable` parts several screens draw with: the `component` package of `:ui`. */
fun DeclarationContainerScope.component() = "Component" {
    title = "共通コンポーネント"
    summary = ":ui モジュールの component package。複数の画面から使われる @Composable 部品"
    example("PrimaryButton", "主要な操作のボタン")
    layout {
        ":ui".module {
            "commonMain".sourceSet / kotlin / modulePackage / "component" / "*".ktFile()
        }
    }
}
