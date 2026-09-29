package com.example.kmp.roles

import com.example.kmp.allowedContents
import com.example.kmp.forbiddenContents
import com.example.kmp.modulePackage
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.dsl.pascalCase

/**
 * The `@Preview` functions themselves.
 *
 * Unlike sample/android, which keeps its `@Preview` functions in the same file as the
 * composable they render, this sample puts them in a `<Target>Preview.kt` file beside it.
 * Both shapes are common; having one sample of each is the point.
 *
 * It is also the one role of this sample that opens two places instead of one, which is why
 * both of them carry a `description`: a role spread over more than one place has to say what
 * tells the two apart, and katachi reports `[MissingDescription]` when it does not.
 */
fun DeclarationContainerScope.preview() = "Preview" {
    title = "Preview"
    summary = "A private @Composable annotated with @Preview. Placed in <Target>Preview.kt in the same package as " +
        "the target Composable, with the content wrapped in PreviewRoot"
    description = """
        A `private @Composable` annotated with `@Preview`. It is written in `<Target>Preview.kt`
        in the same package as what it draws. Keeping it in the same file as the target is also
        possible, but this sample splits the files (the co-located form is in sample/android).

        There are two places. A screen's preview goes in the feature module that holds the
        screen, and a component's preview goes in `:ui` because it belongs to no screen. Both
        follow from the same rule, "next to what it draws".

        This `@Preview` is the one from Compose Multiplatform's
        `org.jetbrains.compose.ui:ui-tooling-preview`. Its fully qualified annotation name is
        exactly the same as that of the Android-only `androidx.compose.ui:ui-tooling-preview`,
        so if IDE completion adds the latter, the iOS target can no longer be resolved.
        Previews can be written in `commonMain` because the former is used.
    """.trimIndent()
    allowedContents = """
        - Previews of stateless Composables that take state as arguments. It calls
          `HomeContent`, not `HomeScreen`, so it can be drawn without building a ViewModel
        - Several per target, one per state. Loading, loaded and failed can be viewed side by side
    """.trimIndent()
    forbiddenContents = """
        - `public` previews. Nothing else calls them, so make them `private`
        - Writing `AppTheme { }` directly in a preview. Wrapping is the job of `PreviewRoot`
        - Anything that touches a real Repository or the network. Write values as literals
    """.trimIndent()
    example("PrimaryButtonPreview", "The preview of PrimaryButton")
    example("HomeLoadedPreview", "The home screen, loaded")
    // A preview lives beside what it renders, so this role claims a file name in two
    // different modules instead of a directory of its own. `component/*.kt` of
    // `Component` covers the same file as well: two roles may claim one path, and the
    // generated documentation shows the pattern on each role's page.
    //
    // katachi reports that overlap as `[AmbiguousLayout]` on the file both patterns match,
    // which is a Warning and never fails `assert()`. It is kept rather than designed away:
    // a preview belongs beside the component it renders, and the report saying so out loud
    // is what this sample wants to show — see `OmittedRoleSelfCheckSpec`, which pins it.
    //
    // In a feature module the name is tied to the module the same way the screen it
    // renders is: `:feature:home` may hold `Home*Preview.kt` and nothing else.
    layout {
        ":feature:${capture("feature")}".module {
            description = "A screen's preview. Placed in the feature module that holds the screen"
            "commonMain".sourceSet / kotlin / modulePackage /
                "${wildcard("feature").pascalCase}*Preview".ktFile()
        }
        ":ui".module {
            description = "A component's preview. Placed in :ui because it belongs to no screen"
            "commonMain".sourceSet / kotlin / modulePackage / "component" / "*Preview".ktFile()
        }
    }
}
