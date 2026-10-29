package com.example.core

import com.example.allowedContents
import com.example.forbiddenContents
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.konture.konture

/**
 * The role of the values the application is about.
 *
 * This is also the one role of the sample with a constraint on the files' contents, written with
 * `konture { }` (katachi's guide: "Konture integration"). It sits directly on the role, so it
 * covers every file the `layout { }` below matches, and is evaluated only when the test passes
 * `FileConstraintCheck()` to `assert()`.
 */
fun DeclarationContainerScope.model() = "Model".role {
    title = "Model"
    summary = "The values the application handles. Holds data classes, enums and value objects"
    description = """
        The values the application handles. `Note` is a data class with a `title` and a `body`; the
        `NoteStore` creates it and `main()` prints it as it is.

        A name says what the value is, with no suffix (`Note`, not `NoteModel`). Only the `.kt` files
        directly in the `model` package are covered; a directory dug below it does not belong to this
        role.

        Among the application's roles, only this one writes a constraint with `konture { }`,
        "Does not depend on the store": a model that refers to anything in the `store` package
        fails the test, before a value starts to know where it is kept.
    """.trimIndent()
    allowedContents = "Data classes, enums, value objects and the computation confined to those values belong here."
    forbiddenContents = """
        - Fetching or saving. I/O belongs to the store role. If a model knew where it is saved, adding
          a single value would mean reading about saving as well
        - Dependencies on external libraries. The model in this sample knows only Kotlin's standard
          library
    """.trimIndent()
    example("Note", "A note with a title and a body")
    // The samples' one `konture { }` constraint: the Konture rule
    // `classes { should() notDependOnPackages "..." }` next to katachi's own layout vocabulary.
    // Written on the role rather than inside `layout { }`, it covers every `.kt` file the layout
    // matches, and nothing else.
    "Does not depend on the store".konture {
        classes { should() notDependOnPackages "com.example.store.." }
    }
    layout {
        // A model is named after the thing it models, so the package is the only marker.
        // Any `.kt` directly in it counts; a subdirectory does not.
        mainSourceSet / kotlin / "com/example/model" / "*".ktFile()
    }
}
