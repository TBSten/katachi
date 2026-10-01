package com.example.roles

import com.example.forbiddenContents
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*

/** The role of the file the server reads to know its port and its entry module. */
fun DeclarationContainerScope.applicationConfig() = "ApplicationConfig" {
    title = "Application configuration"
    summary = "The configuration file loaded at startup: the port and the module to apply"
    description = """
        `application.conf` lives in `src/main/resources` and takes effect at runtime, not at
        build time. It writes the listening port (`18080`, overridable with the environment
        variable `PORT`) and the module applied at startup, `com.example.ApplicationKt.module`.
        It is paired by name with the entry point's `Application.module()`, and fixing only one
        of them stops the server from starting.

        It is also an example of the fact that not only Kotlin files have roles.
    """.trimIndent()
    forbiddenContents = """
        - Build settings. Dependencies and plugins are the Gradle script roles
        - Values that differ per developer, or secrets. `local.properties` is in `.gitignore` and
          is never handed to the check under the default `files = gitTracked()` in the first
          place
    """.trimIndent()
    example("application.conf", "The listening port and the applied module")
    layout {
        // Named rather than `anyFile()`: there is exactly one, and a second file that takes
        // effect at runtime is something to be told about.
        //
        // `resources` is a plain directory, not a source set and not a package: a source set
        // only ever means `src/<name>`, and what sits below it is written out.
        mainSourceSet {
            "resources" {
                "application.conf".file()
            }
        }
    }
}
