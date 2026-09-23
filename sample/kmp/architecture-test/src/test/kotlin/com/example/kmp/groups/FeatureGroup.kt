package com.example.kmp.groups

import com.example.kmp.roles.route
import com.example.kmp.roles.screen
import com.example.kmp.roles.viewModel
import me.tbsten.katachi.dsl.DeclarationContainerScope

/**
 * Roles of one feature: what every `:feature:<name>` module holds.
 *
 * Kept apart from [uiGroup] because the two differ in how they grow. A feature module is a
 * place where things are *expected* to multiply — `":feature:*".module { }` stands for however
 * many there are and reads the matched name back out of `wildcards` — while `:ui` and
 * `:navigation` are shared modules where adding something is a design decision. Folding both
 * into one group would hide that difference in the generated documentation, and would mix two
 * shapes of `layout` inside a single group.
 *
 * The three roles here are where this sample ties a file name to the module it sits in: each
 * one reads `wildcards[0]` back, so `:feature:home` is required to hold `HomeScreen.kt`,
 * `HomeViewModel.kt` and `HomeRoute.kt` — not merely *a* screen, a view model and a route.
 */
fun DeclarationContainerScope.featureGroup() = "feature".group {
    title = "フィーチャー"

    screen()
    viewModel()
    route()
}
