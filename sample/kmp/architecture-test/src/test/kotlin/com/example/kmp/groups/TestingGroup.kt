package com.example.kmp.groups

import com.example.kmp.forbiddenContents
import com.example.kmp.roles.baselineFile
import com.example.kmp.roles.customProcessor
import com.example.kmp.roles.definitionEntry
import com.example.kmp.roles.definitionSections
import com.example.kmp.roles.fake
import com.example.kmp.roles.generatedDocumentation
import com.example.kmp.roles.groupDefinition
import com.example.kmp.roles.layoutSnapshot
import com.example.kmp.roles.processorMetadata
import com.example.kmp.roles.processorSpec
import com.example.kmp.roles.projectArchitectureSpec
import com.example.kmp.roles.projectArchitectureTest
import com.example.kmp.roles.roleDefinition
import com.example.kmp.roles.test
import me.tbsten.katachi.dsl.DeclarationContainerScope

/**
 * Test doubles, the test code itself, the architecture definition it checks, and the two
 * things that definition is written out as.
 *
 * The definition is not a corner of `Test`, because it is not test code: it describes the
 * project, and the test that asserts it is one line. It is split by kind of file
 * (`DefinitionEntry`, `GroupDefinition`, `RoleDefinition` and so on) rather than kept as one
 * catch-all role. Giving it roles is also what keeps `:architecture-test` from being a
 * directory nobody declared.
 */
fun DeclarationContainerScope.testingGroup() = "testing".group {
    title = "Testing support"
    summary = "Test doubles, the test code itself, the katachi architecture definition, the documents and snapshot written from it, and the ledger of shelved violations"
    description = """
        The roles around testing: Fake (commonMain of `:testing`), Test (the tests of each
        module), the roles of `:architecture-test` (DefinitionEntry, DefinitionSections,
        GroupDefinition, RoleDefinition, ProjectArchitectureTest, ProjectArchitectureSpec, and the
        processor package's CustomProcessor, ProcessorMetadata and ProcessorSpec),
        GeneratedDocumentation (`docs/` at the root), LayoutSnapshot (`snapshots/` at the root)
        and BaselineFile (`katachi-baseline.json` at the root, the ledger of shelved violations).

        Keeping the architecture definition apart from Test is the main point of this group. The
        definition is not test code. Its job is to describe the shape of the project, and the
        test that checks it against the real directories is a single line. `:architecture-test`
        holds several kinds of file (the entry, groups, roles, the one test, katachi's own specs,
        custom processors), so each kind is a role and none of them has to be a catch-all.

        Fake living in `commonMain` instead of `commonTest` is also a shape this group shows.
        A test source set cannot be referenced from other modules, so code written for tests
        sometimes has to sit in a production source set. `:testing` is a module cut out for that
        purpose, and nothing in the app itself depends on it.

        GeneratedDocumentation and LayoutSnapshot are separate from the definition roles for the
        same reason. A person writes the definition, `katachiDocs` writes `docs/`, and
        `:architecture-test:test` writes `snapshots/`. The places where you may edit by hand are
        opposite, so merging them into one role would make it impossible to say which one to fix.
        It is also an example of generated output outside `build/` still needing a role.

        The generated files are kept apart from each other too, because their readers differ.
        `docs/` is the page opened by someone who came to read the definition, and `snapshots/`
        is the text seen by whoever reviews the diff of a changed definition.
    """.trimIndent()
    forbiddenContents = """
        - The code of the app itself. What is here is code "for tests" and the description of the project
    """.trimIndent()

    fake()
    test()
    definitionEntry()
    definitionSections()
    groupDefinition()
    roleDefinition()
    projectArchitectureTest()
    projectArchitectureSpec()
    customProcessor()
    processorMetadata()
    processorSpec()
    generatedDocumentation()
    layoutSnapshot()
    baselineFile()
}
