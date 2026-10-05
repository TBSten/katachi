package com.example.groups

import com.example.forbiddenContents
import com.example.roles.architectureDefinitionEntry
import com.example.roles.architectureTest
import com.example.roles.baselineFile
import com.example.roles.documentSectionDefinition
import com.example.roles.generatedDocumentation
import com.example.roles.groupDefinition
import com.example.roles.integrationSpec
import com.example.roles.layoutSnapshot
import com.example.roles.processorDefinition
import com.example.roles.roleDefinition
import com.example.roles.test
import me.tbsten.katachi.dsl.DeclarationContainerScope

/**
 * Roles of the test code, the architecture definition itself, and the two things that
 * definition is written out as.
 *
 * The definition lives in `:architecture-test`, a module that belongs to no layer of the
 * application. It is still code someone has to maintain, so it gets a role of its own rather
 * than hiding inside `Test`: the architecture definition roles describe the shape and `Test` asserts
 * behaviour. `GeneratedDocumentation` and `LayoutSnapshot` are the shape written out — as
 * pages for a reader, and as one flattened line per entry for a reviewer's `git diff`.
 * `BaselineFile` is the ledger of the violations held back, which the test writes too.
 */
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
