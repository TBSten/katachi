package com.example.kmp.roles

import com.example.kmp.modulePackage
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

/** The whole of the `:navigation` module: where a screen can be reached from, and from where. */
fun DeclarationContainerScope.navigation() = "Navigation" {
    title = "ナビゲーション"
    summary = "遷移先の定義と、現在地を持つ Navigator"
    example("Destination", "遷移先の一覧")
    example("Navigator", "現在の遷移先を StateFlow で持つ")
    layout {
        ":navigation".module {
            "commonMain".sourceSet / kotlin / modulePackage / "*".ktFile()
        }
    }
}
