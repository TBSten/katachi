package com.example.groups

import com.example.forbiddenContents
import com.example.roles.architectureEntry
import com.example.roles.architectureTest
import com.example.roles.groupDefinition
import com.example.roles.roleDefinition
import com.example.roles.generatedDocumentation
import com.example.roles.layoutSnapshot
import com.example.roles.processor
import me.tbsten.katachi.dsl.DeclarationContainerScope

/**
 * The architecture definition, the processors this project writes, and what the two produce.
 *
 * The code all lives in `:architecture-test`, a module that belongs to no layer of the
 * application, and it is split into roles rather than kept as one because the direction of
 * reading differs: the definition describes the shape, a processor reads that shape, and `docs/`
 * and `snapshots/` are what was written out of it.
 */
fun DeclarationContainerScope.testingGroup() = "testing".group {
    title = "Definition and processors"
    summary = "The katachi definition, the three processors of your own that read it, and the generated documentation"

    description = """
        The place where this sample's subject lives. It collects the code that supports the project
        rather than a layer of the application.

        The roles are separate because they read in different directions. The architecture definition
        describes a shape, a processor reads that shape and produces something, and the generated
        documentation and the layout snapshot are what was written out. If `processors/` were included
        in a definition role, what this sample wants to show would vanish from both `docs/` and the
        output of `RoleFileCount`.

        The three processors each take one shape: no arguments, typed arguments, and a check (failing
        with `Result.failure`). All of them are `object`s; katachi has no base class to inherit from.

        The two generated outputs are also separate roles. `docs/` is written by `katachiDocs` for
        readers, and `snapshots/` is written by `LayoutSnapshotSpec` for katachi itself, so they differ
        in how they are updated and in who is troubled when one is deleted.
    """.trimIndent()

    forbiddenContents = """
        The main code of the application must not be placed here. `:architecture-test` has no main
        source set.
    """.trimIndent()

    architectureEntry()
    groupDefinition()
    roleDefinition()
    architectureTest()
    processor()
    generatedDocumentation()
    layoutSnapshot()
}
