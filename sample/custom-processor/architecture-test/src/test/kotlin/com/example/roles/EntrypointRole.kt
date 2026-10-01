package com.example.roles

import com.example.allowedContents
import com.example.forbiddenContents
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

/** The role of starting the process and assembling the application. */
fun DeclarationContainerScope.entrypoint() = "Entrypoint" {
    title = "Entrypoint"
    summary = "Starting the process. The only file that has `main()`"
    description = """
        The place where `main()` is written. In this sample it only creates a `NoteStore`, calls
        `all()` and prints the result line by line, with no branching and no configuration.

        The application is deliberately split into three roles because this sample is about
        processors. With a single role, `RoleFileCount` would count one target and `RoleTable` would
        list one row, and nothing could be read from the processors' output. The split is not
        something the size of the application called for.

        The `layout { }` of this role has no wildcard, so it requires exactly one `Main.kt`. Deleting
        it raises `[MissingFile]`.
    """.trimIndent()
    allowedContents = "Only starting the process and assembling the layers belongs here."
    forbiddenContents = """
        - The values themselves. The contents of the notes belong to the store role. If they are
          written directly in `main()`, the answer to "where does the data come from?" moves into the
          entrypoint
        - The shape of a value. The definition of `Note` belongs to the model role
    """.trimIndent()
    example("Main.kt", "Where the process starts")
    layout {
        // The application is the root project, so the path starts at the repository root and the
        // package is written out as `com/example`. No wildcard, so this one is required.
        mainSourceSet / kotlin / "com/example" / "Main".ktFile()
    }
}
