package com.example.roles

import com.example.allowedContents
import com.example.forbiddenContents
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

/** The role of where the values come from. */
fun DeclarationContainerScope.store() = "Store" {
    title = "Store"
    summary = "Takes responsibility for where values come from. For now, fixed values in memory"
    description = """
        The place that takes responsibility for where models are fetched from. `NoteStore` only
        returns two fixed notes, but it draws the boundary that the entrypoint and the model do not
        change even if the storage becomes a file or a database.

        `layout { }` allows the `.kt` files directly in the `store` package. File names are not
        restricted, so the `*Store` convention exists only in this text and is not rejected
        mechanically.
    """.trimIndent()
    allowedContents = """
        Fetching and saving, and the details of the source (connection, path, serialization), belong
        here. Names are aligned as `*Store` and placed directly in the `store` package.
    """.trimIndent()
    forbiddenContents = """
        - Output. `println` is the entrypoint's job. If the store also handled display, there would be
          nothing to swap in a test
        - Definitions of values. `Note` belongs to the model role
    """.trimIndent()
    example("NoteStore", "The list of notes in memory")
    layout {
        mainSourceSet / kotlin / "com/example/store" / "*".ktFile()
    }
}
