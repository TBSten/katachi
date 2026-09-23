package com.example.sample.groups

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
    title = "UI (共通レイヤー)"

    component()
    theme()
    uiCore()
    preview()
    previewRoot()
    navigation()
}
