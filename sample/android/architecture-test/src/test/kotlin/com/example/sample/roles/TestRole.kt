package com.example.sample.roles

import com.example.sample.forbiddenContents
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

/** The role of the tests themselves, told apart from the definition by the file name. */
fun DeclarationContainerScope.test() = "Test" {
    title = "Test code"
    summary = "Tests that check the definition, kept in src/test/kotlin of :architecture-test"
    description = """
        The tests of `:architecture-test`. Every module on the app side is checked through the
        definition written here. The tests in which a feature module checks its own ViewModel
        are a different role (screen test) and live in each feature's `src/test`.

        Files named `*Spec.kt` or `*Test.kt` directly under the package (outside `groups/` and
        `roles/`) are tests. These two points tell them apart from the architecture definition
        role in the same module: putting a test in `roles/` gives an `[UnexpectedFile]`, and
        conversely a definition helper cannot be placed next to the tests.

        There are four now. The only one a user writes is `ProjectArchitectureTest`, a single
        JUnit test that calls `projectArchitecture.assert()`. The rest verify katachi itself:
        `ProjectArchitectureSpec` checks the assembled definition, `LayoutSnapshotSpec` compares
        the flattened layout with the snapshot, and `ProjectRootSpec` watches the result of the
        project root lookup.
    """.trimIndent()
    forbiddenContents = """
        Tools used from tests. Stand-in implementations handed to other modules' tests belong to
        the fake role of `:testing`, and cannot be exposed from `src/test`.
    """.trimIndent()
    example("ProjectArchitectureTest", "The only test a user writes")
    example("ProjectArchitectureSpec", "A test that verifies this definition itself")
    layout {
        // The app modules are checked through `:architecture-test`. The feature
        // modules' own ViewModel tests are the FeatureTest role, not this one.
        //
        // Only the top level of the package: `groups/` and `roles/` hold declarations,
        // never tests, which is what `ArchitectureDefinition` says on its side.
        ":architecture-test".module {
            testSourceSet / kotlin / "com/example/sample" {
                "*Spec".ktFile()
                "*Test".ktFile()
            }
        }
    }
}
