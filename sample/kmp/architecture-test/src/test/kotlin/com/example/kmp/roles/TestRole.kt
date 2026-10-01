package com.example.kmp.roles

import com.example.kmp.forbiddenContents
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

/**
 * The test code of the app itself.
 *
 * Android puts its tests in `src/test`; a KMP module puts them in `commonTest`. Both shapes
 * are one role here.
 */
fun DeclarationContainerScope.test() = "Test" {
    title = "Test code"
    summary = "The tests of each module. commonTest for KMP modules, " +
        "src/test for pure Android / pure JVM modules"
    description = """
        The app's own tests. Where they go depends on the kind of module: `src/test` for an
        Android module and `commonTest` for a KMP module. Either way they are "the tests of that
        module", so katachi treats them as one role.

        What the layout currently writes is only the `testSourceSet` of `:app:android`, because
        that is the only module that actually has tests; when a module with `commonTest`
        appears, one line is added then. Declaring a directory that does not exist would claim
        this sample has a shape it does not have.

        Tests gather in `:app:android` because of KMP. The other modules are KMP with only
        Android and iOS and no JVM target, so this is the only place with JVM tests that can
        exercise the parts that do not use Compose (`Navigator`, `UiState`, `FakeUserRepository`).

        The `@Composable` of screens is not tested. That would need the Compose test runtime,
        which is outside what this sample wants to show.
    """.trimIndent()
    forbiddenContents = """
        - The architecture definition. `:architecture-test` is described by the DefinitionEntry, GroupDefinition, RoleDefinition
          and related roles
        - Test doubles. `Fake*` live in the commonMain of `:testing`
    """.trimIndent()
    example("SampleModulesSpec", "The unit tests of :app:android")
    // Only `:app:android` has test code of its own today, and it is an Android
    // module, so `testSourceSet` is the one place declared. A KMP module would add
    // `"commonTest".sourceSet`; no module in this sample has one yet, and a path
    // declared for a directory that does not exist would claim a shape the sample
    // does not have.
    layout {
        "app/android" {
            testSourceSet / kotlin / "com/example/kmp/app" / "*Spec".ktFile()
        }
    }
}
