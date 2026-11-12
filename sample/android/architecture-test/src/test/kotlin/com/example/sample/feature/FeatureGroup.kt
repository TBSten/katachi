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

fun DeclarationContainerScope.featureGroup() = "Feature".group {
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
        set, `Feature.NewFeature`, from one `--arg feature=`. The module's `build.gradle.kts` and
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
    //   ./gradlew :architecture-test:katachiTemplate --arg template=Feature.NewFeature --arg feature=profile
    "NewFeature".templateSet {
        title = "New feature"
        summary = "A feature module's Route, Screen, ViewModel and one screen part, named after the feature"
        val feature by stringParameter()

        template("Feature.Route") { arg("feature", feature) }
        template("Feature.Screen") { arg("feature", feature) }
        template("Feature.ViewModel") { arg("feature", feature) }
        template("Feature.FeatureComponent") {
            arg("feature", feature)
            arg("name", "${feature.pascalCase}Header")
        }
    }
}

context(layoutScope: LayoutScope)
internal fun featureSources(feature: String = "*"): LayoutDirectory = with(layoutScope) {
    "feature" / feature / mainSourceSet / kotlin / "com/example/sample/feature" / feature
}

enum class FeatureModule {
    Home,
    Settings,
    ;

    val directory: String get() = "feature/${name.lowercase()}"

    val packageDirectory: String get() = "com/example/sample/feature/${name.lowercase()}"
}

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
