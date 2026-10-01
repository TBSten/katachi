package com.example.roles

import com.example.forbiddenContents
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

/** The role of starting the process and assembling the Ktor `Application` module. */
fun DeclarationContainerScope.entrypoint() = "Entrypoint" {
    title = "Entry point"
    summary = "Starting the process and assembling the Ktor Application module"
    description = """
        The single point where the process starts. `Application.kt` holds just two things:
        `main()` and `Application.module()`. `main()` calls `EngineMain.main(args)`, and the engine
        reads `src/main/resources/application.conf` and starts listening.

        `Application.module()` is a function that merely lines up `configureSerialization()` and
        `configureRouting()`. Reading it tells you which cross-cutting settings this app has, and
        in what order. Adding a plugin configuration means touching one line in this list.

        This role's `layout { }` has no wildcard and requires exactly one `Application.kt`.
        Deleting it produces `[MissingFile]`, so the check cannot pass with no entry point
        anywhere.
    """.trimIndent()
    forbiddenContents = """
        - The contents of `install(...)`. The settings themselves belong to the Ktor plugin
          configuration role; only the calls belong here
        - Endpoint registration. `routing { }` is held by `plugin/Routing.kt`
        - The listening port and the list of modules to apply. Those are on the
          `application.conf` (server configuration) side
    """.trimIndent()
    example("Application.kt", "Where the process starts")
    layout {
        // No wildcard, so this one is required: delete `Application.kt` and the check
        // reports `[MissingFile]` instead of silently passing.
        mainSourceSet / kotlin / "com/example" / "Application".ktFile()
    }
}
