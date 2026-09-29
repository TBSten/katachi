package com.example.roles

import com.example.forbiddenContents
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*

/** The role of the files read at runtime — a reminder that not every role is Kotlin. */
fun DeclarationContainerScope.serverConfig() = "ServerConfig" {
    title = "Server configuration"
    summary = "Configuration files loaded at runtime. Resources that are not Kotlin have roles too"
    description = """
        The configuration the started process reads. It lives in `src/main/resources` and takes
        effect at runtime, not at build time. It is also an example of the fact that not only
        Kotlin files have roles.

        `application.conf` writes the listening port (`18080`, overridable with the environment
        variable `PORT`) and the module applied at startup, `com.example.ApplicationKt.module`. It
        is paired by name with the entry point's `Application.module()`, and fixing only one of
        them stops the server from starting. `logback.xml` decides where logs go and their format.

        `layout { }` lists the two files by name rather than with a wildcard. If a third setting
        that takes effect at runtime appears, it should not be allowed to appear silently; it is
        something we want to notice.
    """.trimIndent()
    forbiddenContents = """
        - Build settings. Dependencies and plugins are the Gradle script roles
        - Values that differ per developer, or secrets. `local.properties` is in `.gitignore` and
          is never handed to the check under the default `files = gitTracked()` in the first
          place
    """.trimIndent()
    example("application.conf", "The listening port and the applied module")
    example("logback.xml", "Where logs go and their format")
    layout {
        // Listed one by one rather than with `anyFile()`: there are exactly two of
        // them, and a third one appearing is something to be told about.
        //
        // `resources` is a plain directory, not a source set and not a package: a
        // source set only ever means `src/<name>`, and what sits below it is written
        // out.
        ":".module {
            mainSourceSet {
                "resources" {
                    "application.conf".file()
                    "logback.xml".file()
                }
            }
        }
    }
}
