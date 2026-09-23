package com.example.sample.roles

import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

/** The role Android itself reaches for when it starts the app. */
fun DeclarationContainerScope.entrypoint() = "Entrypoint" {
    title = "エントリポイント"
    summary = ":app に置く、Android がアプリを起動するときに触る型"
    example("MainActivity", "起動時に表示される Activity")
    example("MainApplication", "Application の実装")
    layout {
        ":app".module {
            // Both named exactly, so an app that loses its entry point fails with
            // `[MissingFile]` instead of quietly passing.
            mainSourceSet / kotlin / "com/example/sample" {
                "MainActivity".ktFile()
                "MainApplication".ktFile()
            }
        }
    }
}
