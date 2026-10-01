package com.example.kmp.roles

import com.example.kmp.allowedContents
import com.example.kmp.forbiddenContents
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

/** The shared `@Composable` parts several screens draw with: the `component` package of `:ui`. */
fun DeclarationContainerScope.component() = "Component" {
    title = "Shared component"
    summary = "The component package of the :ui module. @Composable parts used by several screens"
    description = """
        `@Composable` parts used by several screens. They go in the `component` package of the
        `:ui` module. `:ui` is a Compose Multiplatform module with only `commonMain`, so both
        Android and iOS use the same parts.

        When a part is added, add one file here first. Previews are written separately in
        `<PartName>Preview.kt` in the same package (the ui/Preview role), so no `@Preview` goes
        into this role's files. Because `component/*.kt` also matches `*Preview.kt`, katachi
        reports the overlap as `[AmbiguousLayout]`. This is a known and accepted shape.
    """.trimIndent()
    allowedContents = """
        - Parts that know nothing about screens. They take only plain arguments such as `label` and `onClick` and hold no state
        - Reading tokens from the `theme` package (such as `AppSpacing`)
    """.trimIndent()
    forbiddenContents = """
        - Parts used by only one screen. Write them as `internal` in the screen parts of that
          feature module (feature/FeatureComponent)
        - Parts that take `UiState` or a ViewModel. `:ui` does not know about the feature side, so
          if this role knew screen state the dependency would flow backwards
        - Implementations for `androidMain`. If an Android-only View is needed, solve it with
          expect/actual, like ExpectDeclaration / ActualImplementation in `:data`
    """.trimIndent()
    example("PrimaryButton", "The button for the main action")
    layout {
        "ui" {
            "commonMain".sourceSet / kotlin / "com/example/kmp/ui" / "component" / "*".ktFile()
        }
    }
}
