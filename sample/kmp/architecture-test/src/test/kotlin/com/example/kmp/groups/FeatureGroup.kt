package com.example.kmp.groups

import com.example.kmp.forbiddenContents
import com.example.kmp.roles.featureComponent
import com.example.kmp.roles.route
import com.example.kmp.roles.screen
import com.example.kmp.roles.screenPreview
import com.example.kmp.roles.viewModel
import me.tbsten.katachi.dsl.DeclarationContainerScope

/**
 * Roles of one feature: what every `:feature:<name>` module holds.
 *
 * Kept apart from [uiGroup] because the two differ in how they grow. A feature module is a
 * place where things are *expected* to multiply — `":feature:${capture("feature")}".module { }`
 * stands for however many there are and reads the matched name back as `wildcard("feature")` —
 * while `:ui` and
 * `:navigation` are shared modules where adding something is a design decision. Folding both
 * into one group would hide that difference in the generated documentation, and would mix two
 * shapes of `layout` inside a single group.
 *
 * Screen, ViewModel and Route are where this sample ties a file name to the module it sits in:
 * each one reads `wildcard("feature")` back, so `:feature:home` is required to hold
 * `HomeScreen.kt`, `HomeViewModel.kt` and `HomeRoute.kt` — not merely *a* screen, a view model
 * and a route. FeatureComponent reads the same name, and its template is given it
 * (`--arg feature=home`) to pick the module it generates into.
 */
fun DeclarationContainerScope.featureGroup() = "feature".group {
    title = "Feature"
    summary = "One module per screen. :feature:<name> always holds a Screen / ViewModel / Route"
    description = """
        A layer of modules that grows by one per screen. There are `:feature:home` and
        `:feature:settings`; adding a screen means adding a module.

        Screen / ViewModel / Route are collected here because each of them exists for a single
        screen. All three read back the module name that `wildcard("feature")` (that is, `:feature:*`)
        matched and use it to decide the file name, so `:feature:home` needs `HomeScreen.kt` /
        `HomeViewModel.kt` / `HomeRoute.kt`. The shape of this group can say not just "there
        should be a screen" but "a screen with that name is required".

        It is separate from the ui group because the two grow differently. A feature is a place
        where modules are expected to multiply, and one `":feature:${'$'}{capture("feature")}".module { }`
        declaration covers any number of them. Adding something to `:ui` or `:navigation`, on the
        other hand, is a design decision. Merging them into one group would erase that difference
        from the generated documentation.

        A screen's `@Preview` functions (ScreenPreview) sit in the same module as the screen they
        draw.

        Only the screen parts (FeatureComponent) grow in number inside a single feature, and they
        can be generated from a template. The `*` of `:feature:*` is named `feature`, so
        `--arg feature=home` picks the module to generate into.

        Every role in this group is `commonMain`. There is no `androidMain` / `iosMain` around the
        screens. Platform differences stay inside ExpectDeclaration and ActualImplementation of the data group.
    """.trimIndent()
    forbiddenContents = """
        - Parts used by several screens. Those are Component in `:ui`
        - Fetching data. It lives in `:data`, and a feature reads it through an interface
        - Dependencies on other features. Screens do not connect directly; they go through
          `Destination` in `:navigation`
    """.trimIndent()

    screen()
    viewModel()
    route()
    featureComponent()
    screenPreview()
}
