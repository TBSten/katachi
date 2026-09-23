package com.example.sample.groups

import com.example.sample.modulePackage
import com.example.sample.roles.route
import com.example.sample.roles.screen
import com.example.sample.roles.viewModel
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.LayoutDirectory
import me.tbsten.katachi.dsl.LayoutScope
import me.tbsten.katachi.dsl.gradle.*

/**
 * Roles of one feature: what every `:feature:<name>` module holds.
 *
 * Kept apart from [uiGroup] because the two differ in how they grow. A feature module is a
 * place where things are *expected* to multiply — it is written as `":feature:*"` and reads
 * the matched name back out of `wildcards` — while `:ui` and `:navigation` are shared
 * modules where adding something is a design decision. Folding both into one group would
 * hide that difference in the generated documentation, and would mix two shapes of `layout`
 * inside a single group.
 *
 * This is the group where the module path earns its keep twice over. `":feature:*"` stands
 * for the feature modules that exist, so adding `:feature:profile` to `settings.gradle.kts`
 * needs no edit here; and `wildcards[0]` is that module's own name, so a file is not merely
 * allowed to be *a* screen but has to be **that module's** screen. `:feature:home` may hold
 * `HomeScreen.kt` and nothing else called `*Screen.kt`: a `ProfileScreen.kt` left behind
 * there is reported, and a missing `HomeScreen.kt` is reported too, which a `*Screen.kt`
 * could never say.
 *
 * All three roles below start from the same place, so that place is written once as
 * [featureSources] — this project's own addition to the layout vocabulary, not katachi's.
 */
fun DeclarationContainerScope.featureGroup() = "feature".group {
    title = "各画面の構成"

    screen()
    viewModel()
    route()
}

/**
 * Where a feature module keeps its Kotlin sources: `src/main/kotlin` plus the module's own
 * package, which is the start of every path in this group.
 *
 * **This is the project's own vocabulary, written exactly the way katachi writes its own.**
 * `mainSourceSet`, `kotlin` and `modulePackage` are not members of `LayoutScope`; each is a
 * function taking the scope as a context parameter, so one more of them can be added from
 * outside katachi — from here — and reads at the call site like the ones that shipped with
 * it. `with(layoutScope)` is what hands the scope on to them.
 *
 * Nothing here is sugar the DSL had to be taught. `featureSources() / "X".ktFile()` declares
 * the same path `mainSourceSet / kotlin / modulePackage / "X".ktFile()` did, which is why
 * the recorded layout snapshot does not move when a role is rewritten to use it.
 *
 * `internal` and declared next to the group rather than inside one role's file, because all
 * three roles of this group read it. The directory entries it builds are attributed to this
 * file; the `*.kt` entries that follow the `/` still belong to the role that wrote them, so a
 * violation keeps naming the role.
 */
context(layoutScope: LayoutScope)
internal fun featureSources(): LayoutDirectory = with(layoutScope) {
    mainSourceSet / kotlin / modulePackage
}
