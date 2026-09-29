package com.example.roles

import com.example.allowedContents
import com.example.forbiddenContents
import com.example.modulePackage
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

/** The role of the values the application is about. */
fun DeclarationContainerScope.model() = "Model" {
    title = "Model"
    summary = "The values the application handles. Holds data classes, enums and value objects"
    description = """
        The values the application handles. `Note` is a data class with a `title` and a `body`; the
        `NoteStore` creates it and `main()` prints it as it is.

        A name says what the value is, with no suffix (`Note`, not `NoteModel`). Only the `.kt` files
        directly in the `model` package are covered; a directory dug below it does not belong to this
        role.
    """.trimIndent()
    allowedContents = "Data classes, enums, value objects and the computation confined to those values belong here."
    forbiddenContents = """
        - Fetching or saving. I/O belongs to the store role. If a model knew where it is saved, adding
          a single value would mean reading about saving as well
        - Dependencies on external libraries. The model in this sample knows only Kotlin's standard
          library
    """.trimIndent()
    example("Note", "A note with a title and a body")
    layout {
        // A model is named after the thing it models, so the package is the only marker.
        // Any `.kt` directly in it counts; a subdirectory does not.
        ":".module {
            mainSourceSet / kotlin / modulePackage / "model" / "*".ktFile()
        }
    }
}
