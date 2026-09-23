package com.example.roles

import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

/** The role of the katachi definition itself, which belongs to no layer of the application. */
fun DeclarationContainerScope.architectureDefinition() = "ArchitectureDefinition" {
    title = "アーキテクチャ定義"
    summary = "katachi の DSL で書かれた役割の定義。どのレイヤーにも属さない"
    example("ProjectArchitecture.kt", "定義の入口。group ごとの拡張関数を呼ぶ")
    example("roles/ControllerRole.kt", "Controller の役割を宣言する拡張関数")
    layout {
        // The price of the recommended setup: `:architecture-test` checks itself, so
        // the definition has to give itself a role like everything else. The module
        // path resolves to `architecture-test/`, which is where the files actually are.
        //
        // `**` covers `groups/` and `roles/` without naming them, so splitting the
        // definition further costs no line here — and buys no enforcement either: a file
        // under `roles/` that declares no role passes just the same.
        ":architecture-test".module {
            testSourceSet / kotlin / "**" / "*".ktFile()
        }
    }
}
