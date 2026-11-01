package com.example.core

import com.example.core.entrypoint
import com.example.core.model
import com.example.core.store
import com.example.forbiddenContents
import me.tbsten.katachi.dsl.DeclarationContainerScope

fun DeclarationContainerScope.coreGroup() = "core".group {
    title = "Application"
    summary = "A small app that only reads notes and lists them. What the processors read"

    description = """
        The application itself. All it does is read notes from a store and list them on standard
        output.

        It is small on purpose. This sample is about the three processors in the `testing` group, and
        the application is there to provide what they read. For layers, see `api` / `domain` / `data`
        in `sample/jvm`.

        The roles are still split into three because both the output of `RoleFileCount` and the table
        of `RoleTable` should have several rows. With only one, the output would not show what a
        processor did.
    """.trimIndent()

    forbiddenContents = """
        Definitions and processor code must not be placed here. Both live in `:architecture-test` and
        are covered by the roles of the `testing` group.
    """.trimIndent()

    entrypoint()
    model()
    store()
}
