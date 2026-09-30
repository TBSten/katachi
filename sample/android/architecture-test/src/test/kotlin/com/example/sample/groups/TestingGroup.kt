package com.example.sample.groups

import com.example.sample.roles.baselineFile
import com.example.sample.roles.definitionEntry
import com.example.sample.roles.definitionSections
import com.example.sample.roles.fake
import com.example.sample.roles.generatedDocumentation
import com.example.sample.roles.groupDefinition
import com.example.sample.roles.layoutSnapshot
import com.example.sample.roles.projectArchitectureSpec
import com.example.sample.roles.projectArchitectureTest
import com.example.sample.roles.roleDefinition
import me.tbsten.katachi.dsl.DeclarationContainerScope

/**
 * Roles that exist for testing: the shared fakes in `:testing`, the tests themselves, the
 * architecture definition, the two things that definition is written out as, and the baseline
 * of the violations held back.
 *
 * The definition lives in `:architecture-test`, a module that belongs to no layer of the
 * application. It is still code someone has to maintain, so it gets a role of its own
 * rather than hiding inside the test roles: the definition roles (`DefinitionEntry`,
 * `DefinitionSections`, `GroupDefinition`, `RoleDefinition`) describe the shape,
 * `ProjectArchitectureTest` and `ProjectArchitectureSpec` assert behaviour. The two share one module and are told apart by where the file sits:
 * the definition is `ProjectArchitecture.kt` plus the `Group.kt` and `Role.kt` files of the
 * `groups` and `roles` packages, and everything `*Spec.kt` or `*Test.kt` at the top of the
 * package is a test.
 *
 * `:architecture-test` is also the second module whose package does not follow its module
 * path — it would come out as `com/example/sample/architectureTest` — so the roles
 * of this module write `com/example/sample` out as a key. `:testing` does follow it and uses
 * `modulePackage`, which is what makes the difference visible side by side.
 */
fun DeclarationContainerScope.testingGroup() = "testing".group {
    title = "Testing"
    summary = "What exists to verify the app: shared fakes, tests, the architecture definition, generated docs, the layout snapshot and the baseline"
    description = """
        Not a layer of the app, but what exists to verify it. Six kinds of thing are gathered here.
        Fakes are the replacement implementations in `:testing`; test code is the tests
        themselves; the architecture definition is this very definition, written in katachi's
        DSL; generated documentation is the `docs/` that `katachiDocs` writes from that
        definition; the layout snapshot is the `snapshots/` that `:architecture-test:test`
        writes by flattening the same definition; and the baseline is `katachi-baseline.json`,
        the ledger of violations held back.

        The architecture definition is not mixed into the test code because the two say
        different things. The definition says "what shape it has", the tests say "how it
        behaves". Both live in `:architecture-test` and are told apart by file location and
        name (`ProjectArchitecture.kt` plus `groups/` and `roles/` are the definition; the
        `*Spec.kt` and `*Test.kt` at the top of the package are tests). People who rewrite the
        definition and people who add tests are usually doing different things, so they are
        separate entries in the documentation too.

        Only `:testing` is a module on the app side: it puts `Fake*` classes that satisfy the
        interfaces of `:data` in the `main` source set and exposes them to other modules'
        tests. Shipping test code as production code is deliberate, because `src/test` is not
        visible from other modules.

        `:architecture-test` is, like `:app`, a module whose package cannot be derived from the
        module path. Applied as is it would be `com/example/sample/architectureTest`, so the
        definition-side and test roles of this group write `com/example/sample` directly.

        Generated documentation and the snapshot are not even inside a module: they come out in
        `docs/` and `snapshots/` directly under the root. They are here as an example that
        generated files outside `build/` also need roles. The two are not merged because their
        readers differ: `docs/` is the page for someone who came to read the definition, and
        `snapshots/` is the text for someone reviewing the diff of a definition change. The
        hand-written `README.md` at the root is separate from both, and belongs to the
        `Documentation` role of the `tool` group.

        The baseline is the ledger of violations that already existed when katachi was adopted;
        violations recorded there do not fail the test. It is also a file the test writes, so it
        sits here with the generated files.
    """.trimIndent()

    fake()
    projectArchitectureTest()
    projectArchitectureSpec()
    definitionEntry()
    definitionSections()
    groupDefinition()
    roleDefinition()
    generatedDocumentation()
    layoutSnapshot()
    baselineFile()
}
