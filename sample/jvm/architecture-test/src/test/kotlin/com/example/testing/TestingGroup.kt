package com.example.testing

import com.example.forbiddenContents
import com.example.testing.architectureDefinitionEntry
import com.example.testing.architectureTest
import com.example.testing.baselineFile
import com.example.testing.documentSectionDefinition
import com.example.testing.generatedDocumentation
import com.example.testing.groupDefinition
import com.example.testing.integrationSpec
import com.example.testing.layoutSnapshot
import com.example.testing.processorDefinition
import com.example.testing.roleDefinition
import com.example.testing.test
import me.tbsten.katachi.dsl.DeclarationContainerScope

fun DeclarationContainerScope.testingGroup() = "testing".group {
    title = "Testing"
    summary = "Tests that check behaviour, this definition itself, and the documentation and snapshot written out from it"

    description = """
        Not an application layer, but a place that gathers the code that supports the project:
        the test code, the architecture definition itself, and the `docs/` and `snapshots/`
        written out from that definition.

        Tests and the definition are separate because they have different subjects. Tests check
        behaviour; the architecture definition describes shape. If the definition were hidden
        inside `Test`, the documentation would no longer explain why the `:architecture-test`
        module exists.

        What is written out is also kept apart from the definition. The definition is written by
        people, while `docs/` and `snapshots/` are written by machines, so where a hand may touch
        them is completely different. It is also an example of the fact that, once generated files
        live outside `build/`, they need roles too. The two are not merged because their readers
        differ: `docs/` is the pages opened by someone who came to read the definition, and
        `snapshots/` is the text seen by someone reviewing the diff of a definition change.

        `katachi-baseline.json` also belongs here, as a file the test writes out. It is the ledger
        of violations that already existed when katachi was introduced, and the violations recorded
        in it do not fail the test.
    """.trimIndent()

    forbiddenContents = """
        What must not be placed here is the application's own code. `:architecture-test` is a
        module that belongs to no layer of the application and has no main source set.
    """.trimIndent()

    test()
    architectureDefinitionEntry()
    documentSectionDefinition()
    groupDefinition()
    roleDefinition()
    processorDefinition()
    architectureTest()
    integrationSpec()
    generatedDocumentation()
    layoutSnapshot()
    baselineFile()
}
