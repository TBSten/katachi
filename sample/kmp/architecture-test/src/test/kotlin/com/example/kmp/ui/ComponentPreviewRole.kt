package com.example.kmp.ui

import com.example.kmp.allowedContents
import com.example.kmp.feature.screen
import com.example.kmp.feature.screenPreview
import com.example.kmp.forbiddenContents
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

/**
 * The `@Preview` functions of a shared component.
 *
 * A screen's previews are the neighboring [screenPreview] role.
 */
fun DeclarationContainerScope.componentPreview() = "ComponentPreview".role {
    title = "Component preview"
    summary = "A private @Composable annotated with @Preview for a component. Placed in <Target>Preview.kt in the " +
        "component/preview package of :ui, with the content wrapped in PreviewRoot"
    description = """
        A `private @Composable` annotated with `@Preview` that draws a shared component. It is
        written in `<Target>Preview.kt` in the `preview` package right below the `component`
        package of `:ui`, close to the component, because a component belongs to no screen and so
        to no feature module.

        It has a package of its own so that a file is either a component or a preview of one,
        never both. The Component role claims every `.kt` file of `component`; if the previews
        sat in the same package, that pattern would claim them too, and one file would belong to
        two roles.
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
            "commonMain".sourceSet / kotlin / "com/example/kmp/ui" / "component" / "preview" / "*Preview".ktFile()
        }
    }
}
