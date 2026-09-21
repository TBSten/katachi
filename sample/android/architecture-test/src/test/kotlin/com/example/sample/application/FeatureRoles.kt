package com.example.sample.application

import me.tbsten.katachi.dsl.ArchitectureScope

/**
 * Roles of one feature: what every `:feature:<name>` module holds.
 *
 * Kept apart from [uiRoles] because the two differ in how they grow. A feature module is a
 * place where things are *expected* to multiply — step 2 writes it as `":feature:*"` and
 * reads the matched name back out of `wildcards` — while `:ui` and `:navigation` are shared
 * modules where adding something is a design decision. Folding both into one group would
 * hide that difference in the generated documentation, and would mix two shapes of `layout`
 * inside a single group.
 *
 * Deliberately not `inline`. katachi reads the declaration site off the stack trace, and an
 * inlined frame reports a line number remapped past the end of the caller's file, so every
 * role declared here would point at a line that does not exist.
 */
fun ArchitectureScope.featureRoles() {
    "feature".group {
        title = "フィーチャー"

        "Screen" {
            title = "画面"
            summary = "1つの画面の見た目を描く @Composable。:feature:<name> ごとに <Name>Screen.kt を置く"
            example("HomeScreen", "ホーム画面")
            example("SettingsScreen", "設定画面")
            layout { }
        }

        "ViewModel" {
            title = "ビューモデル"
            summary = "画面の状態を StateFlow で公開し、イベントを受け取る androidx.lifecycle.ViewModel"
            example("HomeViewModel", "ホーム画面の状態")
            layout { }
        }

        "Route" {
            title = "ルート"
            summary = "画面への遷移先。feature の外に公開する唯一の入口"
            example("HomeRoute", "ホーム画面への遷移先")
            layout { }
        }
    }
}
