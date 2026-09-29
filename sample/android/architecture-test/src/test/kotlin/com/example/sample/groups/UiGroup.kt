package com.example.sample.groups

import com.example.sample.allowedContents
import com.example.sample.roles.component
import com.example.sample.roles.navigation
import com.example.sample.roles.preview
import com.example.sample.roles.previewRoot
import com.example.sample.roles.theme
import com.example.sample.roles.uiCore
import me.tbsten.katachi.dsl.DeclarationContainerScope

/**
 * Roles of the shared UI: what `:ui` and `:navigation` hold.
 *
 * The per-feature roles (`Screen` / `ViewModel` / `Route`) live in [featureGroup] instead.
 * See the note there for why the two are separate groups.
 *
 * `:ui` is one module split into packages, which is the shape `modulePackage` exists for:
 * `component` / `theme` / `core` / `preview` are named as what they are — one more level
 * below the module's own package — and `mainSourceSet / kotlin / modulePackage` says where
 * that package starts without any role file ever repeating `com/example/sample`.
 */
fun DeclarationContainerScope.uiGroup() = "ui".group {
    title = "UI (shared layer)"
    summary = "UI shared across features: the four packages of :ui, and :navigation"
    description = """
        The UI used by every screen, and its foundation. `:ui` is one module split into
        packages: `component` (shared parts), `theme` (colors and typography), `core` (the
        vocabulary of the UI layer) and `preview` (the base for previews). `:navigation` is a
        separate module that holds only the entry point for screen transitions.

        `:ui` knows neither the layers below nor the features beside it, so no dependency on
        `:data` or `:feature:*` comes in.

        The feature-side Screen / ViewModel / Route are not here because they grow differently.
        That is a place that grows just by adding a module, whereas here every addition is a
        decision about whether every screen will use it, so the groups are separate.

        `:navigation` is a separate module from `:ui` because of the direction of dependencies.
        Features depend only on the `AppNavigator` interface and never touch
        `NavHostController`. It also keeps code that just wants screen parts from pulling in the
        navigation dependency. Building the graph is done by `:app`.
    """.trimIndent()
    allowedContents = """
        Only what two or more features use, or are decided to use, belongs here.
        Anything used by a single screen goes in that feature module.
    """.trimIndent()

    component()
    theme()
    uiCore()
    preview()
    previewRoot()
    navigation()
}
