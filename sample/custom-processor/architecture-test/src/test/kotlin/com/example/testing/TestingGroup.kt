package com.example.testing

import com.example.forbiddenContents
import com.example.testing.architectureDefinitionEntry
import com.example.testing.architectureTest
import com.example.testing.documentSectionDefinition
import com.example.testing.generatedDocumentation
import com.example.testing.groupDefinition
import com.example.testing.integrationSpec
import com.example.testing.layoutSnapshot
import com.example.testing.processor
import com.example.testing.roleDefinition
import me.tbsten.katachi.dsl.DeclarationContainerScope

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

    architectureDefinitionEntry()
    documentSectionDefinition()
    groupDefinition()
    roleDefinition()
    architectureTest()
    integrationSpec()
    processor()
    generatedDocumentation()
    layoutSnapshot()
}
