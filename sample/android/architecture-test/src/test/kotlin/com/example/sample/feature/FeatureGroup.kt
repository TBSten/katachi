@file:OptIn(ExperimentalKatachiApi::class)

package com.example.sample.feature

import com.example.sample.feature.featureComponent
import com.example.sample.feature.featureTest
import com.example.sample.feature.route
import com.example.sample.feature.screen
import com.example.sample.feature.viewModel
import com.example.sample.ui.uiGroup
import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.LayoutDirectory
import me.tbsten.katachi.dsl.LayoutDirectoryScope
import me.tbsten.katachi.dsl.LayoutScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.pascalCase

/**
 * Roles of one feature: what every `:feature:<name>` module holds.
 *
 * Kept apart from [uiGroup] because the two differ in how they grow. A feature module is a
 * place where things are *expected* to multiply — it is written as the directory `"feature" / "*"` —
 * while `:ui` and `:navigation` are shared
 * modules where adding something is a design decision. Folding both into one group would
 * hide that difference in the generated documentation, and would mix two shapes of `layout`
 * inside a single group.
 *
 * This is the group written loosely on purpose. The `*` after `feature` stands for whatever directory sits
 * under it, so adding `:feature:profile` needs no edit here. What the check does tie together is
 * the module and its files: Screen / ViewModel / Route name the module directory and the package
 * directory alike with `capture("feature")` and write the file name as
 * `${capture("feature").pascalCase}Screen`, so a `ProfileScreen.kt` in `:feature:home` is
 * reported as `[CaptureMismatch]`. A missing `HomeScreen.kt` is still not reported: a pattern
 * with a wildcard is never required. FeatureTest is the one role that lists the modules (see
 * [FeatureModule]) and so keeps that tie too.
 *
 * The roles below start from the same place, so that place is written once as
 * [featureSources] — this project's own addition to the layout vocabulary, not katachi's.
 */
fun DeclarationContainerScope.featureGroup() = "feature".group {
    title = "Structure of each screen"
    summary = "A module per screen. Each :feature:<name> holds one Screen / ViewModel / Route, and grows with screen parts and tests"
    description = """
        The contents of a module that holds one screen, such as `:feature:home` or
        `:feature:settings`. Every module has one `<Name>Screen.kt`, `<Name>ViewModel.kt` and
        `<Name>Route.kt`, named after the module. A new screen
        means a new module, so a feature never holds a second screen.

        It is split into three because they change for different reasons. The Screen is the
        look, the ViewModel is the state, and the Route is the seam to the outside; of these,
        only the Route is referenced from outside the feature. `:app` knows only the Route, and
        the Screen and ViewModel stay inside the module.

        The shared layers (`:ui` and `:navigation`) are not in this group because they grow
        differently. A feature is a place where adding is the norm, so it is written as
        the directory `"feature" / "*"`: a new feature module comes under the check without touching
        this definition. Adding to a shared layer is a design
        decision every time, and that lives in the UI (shared layer) group.

        Features do not depend on each other. Even when navigating to another screen, the
        destination is decided on the `:app` side, and the feature only receives one callback.
        The connection exists in a single place in `:app`, so removing a feature does not
        require re-reading the others.

        Screen parts and tests multiply inside one feature. Both start their file names with
        the module name (`HomeUserCard.kt`, `HomeViewModelTest.kt`) and can be generated from
        templates. A screen part is placed by one directory capture, `feature`, written on both
        the module directory and its package directory, so `--arg feature=home` picks the module
        to generate into, the check holds the two directories to the same name, and adding a
        feature does not touch this definition. Only tests name each module one by one, because
        the template content differs per screen; when adding a feature, add one line to
        `FeatureModule` too.

        A new feature's Route, Screen and ViewModel, with one screen part, can be generated as one
        set, `feature.NewFeature`, from one `--arg feature=`. The module's `build.gradle.kts` and
        its `include(":feature:<name>")` in `settings.gradle.kts` are not generated: add them by
        hand, after the other features' includes. To show the screen, `:app` also needs
        `implementation(project(":feature:<name>"))` and the one line `<name>Screen(...)` in its
        NavHost, the one line the Route role says `:app` grows by.
    """.trimIndent()

    screen()
    viewModel()
    route()
    featureComponent()
    featureTest()

    // A whole feature from its name, as one set: Route, Screen and ViewModel named after it, and
    // one screen part. The module's build.gradle.kts and its `include` are not files any role
    // here holds, so they are written by hand; FeatureTest stays out, as its template is
    // written per module.
    //   ./gradlew :architecture-test:katachiTemplate --arg template=feature.NewFeature --arg feature=profile
    "NewFeature".templateSet {
        title = "New feature"
        summary = "A feature module's Route, Screen, ViewModel and one screen part, named after the feature"
        val feature by stringParameter()

        template("feature.Route") { arg("feature", feature) }
        template("feature.Screen") { arg("feature", feature) }
        template("feature.ViewModel") { arg("feature", feature) }
        template("feature.FeatureComponent") {
            arg("feature", feature)
            arg("name", "${feature.pascalCase}Header")
        }
    }
}

/**
 * Where a feature module keeps its Kotlin sources: `feature/<module>/src/main/kotlin` plus the
 * module's own package, which is the start of every path in this group.
 *
 * **This is the project's own vocabulary, written exactly the way katachi writes its own.**
 * `mainSourceSet` and `kotlin` are not members of `LayoutScope`; each is a function taking
 * the scope as a context parameter, so one more of them can be added from outside katachi —
 * from here — and reads at the call site like the ones that shipped with it. `with(layoutScope)`
 * is what hands the scope on to them.
 *
 * [feature] is the one directory name that varies per feature, and it is written twice: the
 * module directory and the package directory below the source set are the same name. With the
 * default `*` the two are checked apart, as any module and any package. A role passes
 * `capture("feature")` instead to tie them together: the check then allows only a file whose two
 * levels read the same value, and a template fills both from one `--arg feature=`.
 *
 * `internal` and declared next to the group rather than inside one role's file, because the
 * roles of this group read it. The directory entries it builds are attributed to this
 * file; the `*.kt` entries that follow the `/` still belong to the role that wrote them, so a
 * violation keeps naming the role.
 */
context(layoutScope: LayoutScope)
internal fun featureSources(feature: String = "*"): LayoutDirectory = with(layoutScope) {
    "feature" / feature / mainSourceSet / kotlin / "com/example/sample/feature" / feature
}

/**
 * The feature modules this project has, by name: `Home` is `:feature:home`.
 *
 * Only for a template whose *content* differs per module. FeatureTest arranges a different
 * fake for each screen, so its template is a `when` over this enum, and [eachFeatureModule]
 * declares its layout module by module, one `.template` per module with the module's own id
 * (`--arg template=feature.FeatureTest.home`); a new module fails to compile there until the
 * new screen is taught to it. The other feature roles do not list modules; they use the
 * directory `"feature" / "*"`.
 *
 * The check keeps it honest in one direction: an entry whose module was deleted is reported
 * as that module's missing `build.gradle.kts`. A new module missing from it is not reported
 * until a file of that role is put there, which then shows up as `[UnexpectedFile]`
 * — add the entry when adding the module to `settings.gradle.kts`.
 */
enum class FeatureModule {
    Home,
    Settings,
    ;

    /** The module directory, `feature/home`. */
    val directory: String get() = "feature/${name.lowercase()}"

    /** The package directory below the source set, `com/example/sample/feature/home`. */
    val packageDirectory: String get() = "com/example/sample/feature/${name.lowercase()}"
}

/**
 * Declares [block] once for every [FeatureModule], inside that module's directory.
 *
 * The concrete counterpart of `featureSources()`: where that one matches any module, this one
 * hands the [FeatureModule] to [block], for a template that branches on it.
 */
context(layoutScope: LayoutScope)
internal fun eachFeatureModule(block: LayoutDirectoryScope.(FeatureModule) -> Unit) {
    with(layoutScope) {
        FeatureModule.entries.forEach { feature ->
            feature.directory {
                // Two or more places for one role want a sentence each on when to pick it.
                description = "For `:feature:${feature.name.lowercase()}`. Start the file name with `${feature.name}`"
                block(feature)
            }
        }
    }
}
