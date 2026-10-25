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
 * place where things are *expected* to multiply — the directory `"feature" / "*"`
 * stands for however many there are — while `:ui` and
 * `:navigation` are shared modules where adding something is a design decision. Folding both
 * into one group would hide that difference in the generated documentation, and would mix two
 * shapes of `layout` inside a single group.
 *
 * The `*` after `feature` stands for whatever directory sits under it, so adding
 * `:feature:profile` needs no edit here. What the check does tie together is the module and its
 * files, the same way sample/android does: Screen / ViewModel / Route name the module directory
 * and the package directory alike with `capture("feature")` and write the file name as
 * `${capture("feature").pascalCase}Screen`, so a `ProfileScreen.kt` in `:feature:home` is
 * reported as `[CaptureMismatch]`. FeatureComponent uses the same `capture("feature")`, so its
 * template is given one value (`--arg feature=home`) to pick the module it generates into.
 */
fun DeclarationContainerScope.featureGroup() = "feature".group {
    title = "Feature"
    summary = "One module per screen. :feature:<name> always holds a Screen / ViewModel / Route"
    description = """
        A layer of modules that grows by one per screen. There are `:feature:home` and
        `:feature:settings`; adding a screen means adding a module.

        Screen / ViewModel / Route are collected here because each of them exists for a single
        screen. Each file is named after the module: `:feature:home` has `HomeScreen.kt` /
        `HomeViewModel.kt` / `HomeRoute.kt`, and the check holds them to it, so a
        `ProfileScreen.kt` in `:feature:home` is reported.

        It is separate from the ui group because the two grow differently. A feature is a place
        where modules are expected to multiply, and one declaration of the directory `feature/*`
        covers any number of them. Adding something to `:ui` or `:navigation`, on the
        other hand, is a design decision. Merging them into one group would erase that difference
        from the generated documentation.

        A screen's `@Preview` functions (ScreenPreview) sit in the same module as the screen they
        draw.

        Only the screen parts (FeatureComponent) grow in number inside a single feature, and they
        can be generated from a template. The module directory and its package directory are
        both named `feature`, so `--arg feature=home` picks the module to generate into.

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
