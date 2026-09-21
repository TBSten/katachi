package com.example.sample.application

import com.example.sample.modulePackage
import me.tbsten.katachi.dsl.ArchitectureScope
import me.tbsten.katachi.dsl.pascalCase

/**
 * Roles of one feature: what every `:feature:<name>` module holds.
 *
 * Kept apart from [uiRoles] because the two differ in how they grow. A feature module is a
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
 * Deliberately not `inline`. katachi reads the declaration site off the stack trace, and an
 * inlined frame reports a line number remapped past the end of the caller's file, so every
 * role declared here would point at a line that does not exist.
 */
fun ArchitectureScope.featureRoles() {
    "feature".group {
        title = "各画面の構成"

        "Screen" {
            title = "画面"
            summary = "1つの画面の見た目を描く @Composable。:feature:<name> ごとに <Name>Screen.kt を置く"
            example("HomeScreen", "ホーム画面")
            example("SettingsScreen", "設定画面")
            layout {
                ":feature:*".module {
                    mainSourceSet / kotlin / modulePackage / "${wildcards[0].pascalCase}Screen".ktFile()
                }
            }
        }

        "ViewModel" {
            title = "ビューモデル"
            summary = "画面の状態を StateFlow で公開し、イベントを受け取る androidx.lifecycle.ViewModel"
            example("HomeViewModel", "ホーム画面の状態")
            layout {
                ":feature:*".module {
                    mainSourceSet / kotlin / modulePackage / "${wildcards[0].pascalCase}ViewModel".ktFile()
                }
            }
        }

        "Route" {
            title = "ルート"
            summary = "画面への遷移先。feature の外に公開する唯一の入口"
            example("HomeRoute", "ホーム画面への遷移先")
            layout {
                ":feature:*".module {
                    mainSourceSet / kotlin / modulePackage / "${wildcards[0].pascalCase}Route".ktFile()
                }
            }
        }
    }
}
