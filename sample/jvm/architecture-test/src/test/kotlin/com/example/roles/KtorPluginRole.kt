package com.example.roles

import com.example.allowedContents
import com.example.forbiddenContents
import com.example.modulePackage
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

/** The role of one cross-cutting setting installed into the Ktor `Application`. */
fun DeclarationContainerScope.ktorPlugin() = "KtorPlugin" {
    title = "Ktor plugin configuration"
    summary = "Applies one cross-cutting setting to the Ktor Application"
    description = """
        A place for settings that take effect once for the whole Application, not for a specific
        endpoint. Each file holds one extension function on `Application`, `configureXxx()`, and
        the entry point's `Application.module()` calls them in turn.

        File names have no suffix. There is no cue like `*Controller`, so it is the `plugin`
        package itself that says "this is a plugin configuration". The file name matches the name
        of the Ktor feature being installed.
    """.trimIndent()
    allowedContents = """
        Only a Ktor plugin's `install(...)` and its configuration block may be placed here.
        `Serialization.kt` installs JSON (with `prettyPrint` and `ignoreUnknownKeys` enabled) into
        `ContentNegotiation`, and `Routing.kt` opens `routing { }` and calls each Controller's
        `register`. The aim is to see in one file which Controllers are connected.
    """.trimIndent()
    forbiddenContents = """
        - The body of an endpoint handler. What goes inside `get`/`post` is the controller's role
        - Domain decisions or data fetching. Do not call a Service or Repository from here
        - `main()` and `Application.module()`. Starting and assembling belong to the entry point
          role
    """.trimIndent()
    example("Routing", "Wiring of the routing tree")
    example("Serialization", "JSON input/output configuration")
    layout {
        // No suffix to key on: a plugin file is named after the Ktor feature it
        // installs, so the package itself is what says "this is a plugin".
        ":".module {
            mainSourceSet / kotlin / modulePackage / "plugin" / "*".ktFile()
        }
    }
}
