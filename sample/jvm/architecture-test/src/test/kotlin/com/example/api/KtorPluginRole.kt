@file:OptIn(ExperimentalKatachiApi::class)

package com.example.api

import com.example.allowedContents
import com.example.forbiddenContents
import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.konsist.declarations.ktFileContent

fun DeclarationContainerScope.ktorPlugin() = "KtorPlugin".role {
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
        mainSourceSet / kotlin / "com/example/plugin" / "*".ktFile()
    }
    // `fileName` is a plain String, so the one function a file holds is named from it:
    // Routing.kt holds `Application.configureRouting()` and nothing else. The imports leave out
    // the Services and Repositories, which a plugin configuration never calls.
    ktFileContent {
        `package`("com.example.plugin")

        `import`("io.ktor.**")
        `import`("kotlinx.serialization.**")
        `import`("com.example.controller.**")

        required(`fun`, "configure$fileName")()
    }
}
