package com.example.kmp.application

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
 * Not `inline`: an inlined call reports the caller's file with a line number remapped past
 * the end of that file, so every declaration below would record a position that does not
 * exist.
 *
 * Every layout here is spelled out as plain directories, down to the package. `:feature:home`
 * is the directory `feature/home`, `commonMain` is the directory `src/commonMain/kotlin`, and
 * the package is the directory chain below it. The `*` in `feature / "*"` is the module name
 * and the second one is the package matching it, which is what `":feature:*"` will say in one
 * go from step 3 onwards.
 */
fun ArchitectureScope.featureRoles() {
    "feature".group {
        title = "フィーチャー"

        "Screen" {
            title = "画面"
            summary = "1つの画面の @Composable。ViewModel の StateFlow を購読し、Component を組み合わせて描く"
            example("HomeScreen", "ホーム画面")
            example("SettingsScreen", "設定画面")
            layout {
                "feature" / "*" / "src/commonMain/kotlin" {
                    "com/example/kmp/feature" / "*" / "*Screen".ktFile()
                }
            }
        }
        "ViewModel" {
            title = "ViewModel"
            summary = "画面の状態を持つ androidx.lifecycle.ViewModel。" +
                "Repository から取得した値を UiState に変換し、StateFlow で公開する"
            example("HomeViewModel", "ホーム画面の状態")
            layout {
                "feature" / "*" / "src/commonMain/kotlin" {
                    "com/example/kmp/feature" / "*" / "*ViewModel".ktFile()
                }
            }
        }
        "Route" {
            title = "ルート"
            summary = "画面を navigation の Destination に結びつけ、ViewModel の生成も引き受ける"
            example("HomeRoute", "ホーム画面の遷移先")
            layout {
                "feature" / "*" / "src/commonMain/kotlin" {
                    "com/example/kmp/feature" / "*" / "*Route".ktFile()
                }
            }
        }
    }
}
