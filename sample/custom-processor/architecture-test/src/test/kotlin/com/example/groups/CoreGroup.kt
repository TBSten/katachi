package com.example.groups

import com.example.forbiddenContents
import com.example.roles.entrypoint
import com.example.roles.model
import com.example.roles.store
import me.tbsten.katachi.dsl.DeclarationContainerScope

/**
 * Roles of the application itself: the smallest thing the processors can be pointed at.
 *
 * It is one group and not three layers on purpose. This sample's subject is in `testing`, and a
 * layered application here would invite a reader to compare architectures instead of reading
 * processors -- `sample/jvm`, `sample/android` and `sample/kmp` are where that comparison
 * belongs.
 */
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
