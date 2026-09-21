package com.example.sample.application

import com.example.sample.modulePackage
import me.tbsten.katachi.dsl.ArchitectureScope
import me.tbsten.katachi.dsl.LayoutDirectory
import me.tbsten.katachi.dsl.LayoutScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.ktFile
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
 * All three roles below start from the same place, so that place is written once as
 * [featureSources] — this project's own addition to the layout vocabulary, not katachi's.
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
                    featureSources() / "${wildcards[0].pascalCase}Screen".ktFile()
                }
            }
        }

        "ViewModel" {
            title = "ビューモデル"
            summary = "画面の状態を StateFlow で公開し、イベントを受け取る androidx.lifecycle.ViewModel"
            example("HomeViewModel", "ホーム画面の状態")
            layout {
                ":feature:*".module {
                    featureSources() / "${wildcards[0].pascalCase}ViewModel".ktFile()
                }
            }
        }

        "Route" {
            title = "ルート"
            summary = "画面への遷移先。feature の外に公開する唯一の入口"
            example("HomeRoute", "ホーム画面への遷移先")
            layout {
                ":feature:*".module {
                    featureSources() / "${wildcards[0].pascalCase}Route".ktFile()
                }
            }
        }
    }
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
 */
context(layoutScope: LayoutScope)
private fun featureSources(): LayoutDirectory = with(layoutScope) {
    mainSourceSet / kotlin / modulePackage
}
