package com.example.roles

import com.example.allowedContents
import com.example.forbiddenContents
import com.example.modulePackage
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

/** The role that owns where a value comes from, so the domain does not have to know. */
fun DeclarationContainerScope.repository() = "Repository" {
    title = "Repository"
    summary = "Handles fetching and saving data, hiding data source details from the domain"
    description = """
        The place that takes responsibility for where a value comes from. A Service only calls
        `HealthRepository.load()` and does not know whether what lies behind it is a DB, a file or
        a fixed value. The aim is to confine what needs fixing when the data source changes to
        within this role.

        The file name is `*Repository.kt`, one class per file.
    """.trimIndent()
    allowedContents = """
        Only the round trip to an external data source and repacking the result into models may be
        placed here. This sample's `HealthRepository` has no DB and simply returns
        `Health(status = "UP", version = "0.1.0")`, but in terms of what katachi looks at, "where
        it is placed and what it is named", it has the same shape as a real one.
    """.trimIndent()
    forbiddenContents = """
        - Application-specific decisions. What to prioritize and how to combine things is the
          service's role
        - Ktor types. An HTTP request never comes down this far
        - Returning a type specific to the data source to the outside. Return values are aligned
          to models
    """.trimIndent()
    example("HealthRepository", "The source of the running status")
    layout {
        ":".module {
            mainSourceSet / kotlin / modulePackage / "repository" / "*Repository".ktFile()
        }
    }
}
