package com.example.sample.roles

import com.example.sample.forbiddenContents
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

/** The role of the Application class Android creates before any Activity. */
fun DeclarationContainerScope.applicationEntrypoint() = "ApplicationEntrypoint" {
    title = "Application entrypoint"
    summary = "The single Application class Android creates at process start, kept in :app"
    description = """
        The `Application` Android creates once when the process starts. `:app` has one
        `MainApplication`, named exactly. If it is gone, the check fails with `[MissingFile]`.

        `MainApplication` only extends `Application`. It is left empty as the place to add "once
        at launch" work such as initializing a DI container.

        The package is written directly as `com/example/sample` for the same reason as
        `MainActivity`: `:app` is the application itself.
    """.trimIndent()
    forbiddenContents = """
        Screen contents. Anything drawn on screen belongs to `:ui` or `:feature:*`.
    """.trimIndent()
    example("MainApplication", "The Application implementation")
    layout {
        "app" {
            mainSourceSet / kotlin / "com/example/sample" {
                "MainApplication".ktFile()
            }
        }
    }
}
