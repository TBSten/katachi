package com.example.sample.roles

import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

/** The role of the katachi definition itself, which belongs to no layer of the application. */
fun DeclarationContainerScope.architectureDefinition() = "ArchitectureDefinition" {
    title = "アーキテクチャ定義"
    summary = "katachi の DSL で書かれた役割の定義。どのレイヤーにも属さない"
    example("ProjectArchitecture.kt", "定義の入口。group ごとの拡張関数を呼ぶ")
    example("roles/ScreenRole.kt", "Screen の役割を宣言する拡張関数")
    layout {
        ":architecture-test".module {
            testSourceSet / kotlin / "com/example/sample" {
                "ProjectArchitecture".ktFile()
                // One declaration per file, and the file name says which kind it is:
                // `groups/` holds `*Group.kt` and `roles/` holds `*Role.kt`, so a helper
                // dropped into either is reported as `[UnexpectedFile]` rather than
                // quietly becoming a third kind of file.
                "groups" { "*Group".ktFile() }
                "roles" { "*Role".ktFile() }
            }
        }
    }
}
