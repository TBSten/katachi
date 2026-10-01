package com.example.roles

import com.example.forbiddenContents
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*

/** The role of the file that decides where logs go and in what format. */
fun DeclarationContainerScope.loggingConfig() = "LoggingConfig" {
    title = "Logging configuration"
    summary = "The Logback configuration: where logs go and their format"
    description = """
        `logback.xml` lives in `src/main/resources` and is read by Logback when the process
        starts. It decides where logs go and their format. It is not Kotlin and not the server's
        own configuration, so it is a role of its own next to `application.conf`.
    """.trimIndent()
    forbiddenContents = """
        - Server settings such as the port. Those are in `application.conf`
    """.trimIndent()
    example("logback.xml", "Where logs go and their format")
    layout {
        mainSourceSet {
            "resources" {
                "logback.xml".file()
            }
        }
    }
}
