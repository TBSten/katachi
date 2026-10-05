package com.example.groups

import com.example.roles.applicationConfig
import com.example.roles.entrypoint
import com.example.roles.loggingConfig
import me.tbsten.katachi.dsl.DeclarationContainerScope

/** Roles that assemble and configure the running process. */
fun DeclarationContainerScope.appGroup() = "app".group {
    title = "Application"
    summary = "Starting the process, and the configuration loaded at runtime"

    description = """
        A layer that gathers what answers "how does this process start?". Rather than a layer, it
        is a home for the startup code that belongs to no other layer.

        It holds the entry point (`Application.kt`) and the server configuration
        (`application.conf` and `logback.xml`). The two are a pair: `modules` in `application.conf`
        names `com.example.ApplicationKt.module`, and that function calls each plugin
        configuration. Fixing only one of them stops the server from starting, so they can be
        read in the same place.

        What is not placed here: settings that only take effect at build time (the Gradle script
        roles) and the contents of each `install(...)` (the Ktor plugin configuration role in the
        API layer).
    """.trimIndent()

    entrypoint()
    applicationConfig()
    loggingConfig()
}
