package com.example.roles

import com.example.modulePackage
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

/** The role of starting the process and assembling the Ktor `Application` module. */
fun DeclarationContainerScope.entrypoint() = "Entrypoint" {
    title = "エントリポイント"
    summary = "プロセスの起動と、Ktor の Application モジュールの組み立て"
    example("Application.kt", "main() と Application.module()")
    layout {
        // No wildcard, so this one is required: delete `Application.kt` and the check
        // reports `[MissingFile]` instead of silently passing.
        ":".module {
            mainSourceSet / kotlin / modulePackage / "Application".ktFile()
        }
    }
}
