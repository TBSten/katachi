package com.example.kmp.groups

import com.example.kmp.forbiddenContents
import com.example.kmp.roles.architectureDefinition
import com.example.kmp.roles.baselineFile
import com.example.kmp.roles.fake
import com.example.kmp.roles.generatedDocumentation
import com.example.kmp.roles.layoutSnapshot
import com.example.kmp.roles.test
import me.tbsten.katachi.dsl.DeclarationContainerScope

/**
 * Test doubles, the test code itself, the architecture definition it checks, and the two
 * things that definition is written out as.
 *
 * `ArchitectureDefinition` is a role of its own rather than a corner of `Test`, because the
 * definition is not test code: it describes the project, and the test that asserts it is one
 * line. Giving it a role is also what keeps `:architecture-test` from being a directory nobody
 * declared.
 */
fun DeclarationContainerScope.testingGroup() = "testing".group {
    title = "Testing support"
    summary = "Test doubles, the test code itself, the katachi architecture definition, the documents and snapshot written from it, and the ledger of shelved violations"
    description = """
        Six roles around testing: Fake (commonMain of `:testing`), Test (the tests of each
        module), ArchitectureDefinition (`:architecture-test`), GeneratedDocumentation (`docs/`
        at the root), LayoutSnapshot (`snapshots/` at the root) and BaselineFile (`katachi-baseline.json`
        at the root, the ledger of shelved violations).

        Keeping ArchitectureDefinition apart from Test is the main point of this group. The
        architecture definition is not test code. Its job is to describe the shape of the project,
        and the test that checks it against the real directories is a single line. Without giving
        it a role, `:architecture-test` would be a directory nobody declared.

        Fake living in `commonMain` instead of `commonTest` is also a shape this group shows.
        A test source set cannot be referenced from other modules, so code written for tests
        sometimes has to sit in a production source set. `:testing` is a module cut out for that
        purpose, and nothing in the app itself depends on it.

        GeneratedDocumentation and LayoutSnapshot are separate from ArchitectureDefinition for the
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
    architectureDefinition()
    generatedDocumentation()
    layoutSnapshot()
    baselineFile()
}
