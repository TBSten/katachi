package com.example.kmp.roles

import com.example.kmp.allowedContents
import com.example.kmp.forbiddenContents
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

/**
 * The `@Preview` functions of a shared component.
 *
 * A screen's previews are the neighboring [screenPreview] role.
 */
fun DeclarationContainerScope.componentPreview() = "ComponentPreview" {
    title = "Component preview"
    summary = "A private @Composable annotated with @Preview for a component. Placed in <Target>Preview.kt in the " +
        "component package of :ui, with the content wrapped in PreviewRoot"
    description = """
        A `private @Composable` annotated with `@Preview` that draws a shared component. It is
        written in `<Target>Preview.kt` in the `component` package of `:ui`, next to the
        component, because a component belongs to no screen and so to no feature module.

        The Component role claims every `.kt` file of the same package, and this role claims the
        ones ending in `Preview.kt`, so the one file matching both belongs to both roles. katachi
        reports that overlap as `[AmbiguousLayout]`, which is a Warning and never fails
        `assert()`. It is kept rather than designed away: a preview belongs beside the component
        it renders, and the report saying so out loud is what this sample wants to show — see
        `OmittedRoleSelfCheckSpec`, which pins it.
    """.trimIndent()
    allowedContents = """
        - Previews of stateless components that take their state as arguments
        - Several per target, one per state
    """.trimIndent()
    forbiddenContents = """
        - `public` previews. Nothing else calls them, so make them `private`
        - Writing `AppTheme { }` directly in a preview. Wrapping is the job of `PreviewRoot`
        - Previews of screens. They belong to the ScreenPreview role in the feature module
    """.trimIndent()
    example("PrimaryButtonPreview", "The preview of PrimaryButton")
    layout {
        "ui" {
            "commonMain".sourceSet / kotlin / "com/example/kmp/ui" / "component" / "*Preview".ktFile()
        }
    }
}
