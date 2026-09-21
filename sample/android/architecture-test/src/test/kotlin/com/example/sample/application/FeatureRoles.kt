package com.example.sample.application

import me.tbsten.katachi.dsl.ArchitectureScope
import me.tbsten.katachi.dsl.LayoutDirectory
import me.tbsten.katachi.dsl.LayoutScope

/**
 * Roles of one feature: what every `:feature:<name>` module holds.
 *
 * Kept apart from [uiRoles] because the two differ in how they grow. A feature module is a
 * place where things are *expected* to multiply — step 3 writes it as `":feature:*"` and
 * reads the matched name back out of `wildcards` — while `:ui` and `:navigation` are shared
 * modules where adding something is a design decision. Folding both into one group would
 * hide that difference in the generated documentation, and would mix two shapes of `layout`
 * inside a single group.
 *
 * Step 2 has neither module paths nor wildcard capture, so each feature module is spelled
 * out once per role by [featureSources]. That repetition is the point of the step: step 3
 * replaces the two calls with one `":feature:*".module { }` and has to come out with
 * exactly the same check result.
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
                featureSources("home") / "*Screen".ktFile()
                featureSources("settings") / "*Screen".ktFile()
            }
        }

        "ViewModel" {
            title = "ビューモデル"
            summary = "画面の状態を StateFlow で公開し、イベントを受け取る androidx.lifecycle.ViewModel"
            example("HomeViewModel", "ホーム画面の状態")
            layout {
                featureSources("home") / "*ViewModel".ktFile()
                featureSources("settings") / "*ViewModel".ktFile()
            }
        }

        "Route" {
            title = "ルート"
            summary = "画面への遷移先。feature の外に公開する唯一の入口"
            example("HomeRoute", "ホーム画面への遷移先")
            layout {
                featureSources("home") / "*Route".ktFile()
                featureSources("settings") / "*Route".ktFile()
            }
        }
    }
}

/**
 * The one directory a feature module keeps its sources in:
 * `feature/<name>/src/main/kotlin/com/example/sample/feature/<name>`.
 *
 * Written as a `/` chain rather than as one multi-level key so that the sample exercises the
 * chaining operators, and as a function because step 2 knows nothing about modules, source
 * sets or packages — every level of the path is a plain directory here. The module name
 * appears twice because the Gradle path and the Kotlin package happen to agree; step 3
 * derives both from a single `wildcards[0]`.
 *
 * Calling it from inside `layout { }` is safe: the declaration site katachi captures is the
 * first frame outside its own packages, which is the line below, in this file.
 */
private fun LayoutScope.featureSources(name: String): LayoutDirectory =
    "feature" / name / "src" / "main" / "kotlin" / "com" / "example" / "sample" / "feature" / name
