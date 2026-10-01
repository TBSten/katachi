package com.example.sample.groups

import com.example.sample.roles.featureComponent
import com.example.sample.roles.featureTest
import com.example.sample.roles.route
import com.example.sample.roles.screen
import com.example.sample.roles.viewModel
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.LayoutDirectory
import me.tbsten.katachi.dsl.LayoutDirectoryScope
import me.tbsten.katachi.dsl.LayoutScope
import me.tbsten.katachi.dsl.gradle.*

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
 * under it, so adding `:feature:profile` needs no edit here, and `*Screen.kt` accepts
 * any screen. The price is that the check does not tie a file name to its module: a
 * `ProfileScreen.kt` in `:feature:home` is not reported, and a missing `HomeScreen.kt` is not
 * either. FeatureTest is the one role that lists the modules (see [FeatureModule]) and so
 * keeps that tie.
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
        templates. A screen part is placed by two directory captures (`feature` for the module,
        `featurePackage` for its package), so `--arg feature=home --arg featurePackage=home`
        picks the module to generate into and adding a feature does not touch this definition.
        Only tests name each module one by one, because the template content differs per
        screen; when adding a feature, add one line to `FeatureModule` too.
    """.trimIndent()

    screen()
    viewModel()
    route()
    featureComponent()
    featureTest()
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
 * [module] and [packageName] are the two directory names that vary per feature. Both default
 * to `*`, which is what Screen / ViewModel / Route want: any module, any package. A role that
 * needs to read either one back passes `capture("...")` instead. They are two names, not one,
 * because a capture name may be used only once in a path.
 *
 * `internal` and declared next to the group rather than inside one role's file, because the
 * roles of this group read it. The directory entries it builds are attributed to this
 * file; the `*.kt` entries that follow the `/` still belong to the role that wrote them, so a
 * violation keeps naming the role.
 */
context(layoutScope: LayoutScope)
internal fun featureSources(
    module: String = "*",
    packageName: String = "*",
): LayoutDirectory = with(layoutScope) {
    "feature" / module / mainSourceSet / kotlin / "com/example/sample/feature" / packageName
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
