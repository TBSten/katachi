package com.example.kmp.application

import com.example.kmp.modulePackage
import me.tbsten.katachi.dsl.ArchitectureScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.dsl.pascalCase

/**
 * Roles of one feature: what every `:feature:<name>` module holds.
 *
 * Kept apart from [uiRoles] because the two differ in how they grow. A feature module is a
 * place where things are *expected* to multiply — `":feature:*".module { }` stands for however
 * many there are and reads the matched name back out of `wildcards` — while `:ui` and
 * `:navigation` are shared modules where adding something is a design decision. Folding both
 * into one group would hide that difference in the generated documentation, and would mix two
 * shapes of `layout` inside a single group.
 *
 * Not `inline`: an inlined call reports the caller's file with a line number remapped past
 * the end of that file, so every declaration below would record a position that does not
 * exist.
 *
 * The three roles here are where this sample ties a file name to the module it sits in.
 * `wildcards[0]` is what `:feature:*` matched — `home` for `:feature:home` — so the file
 * required of that module is `HomeScreen.kt` and nothing else: a `ProfileScreen.kt` under
 * `:feature:home` is an `[UnexpectedFile]`, and it is `[MissingFile]` that reports a feature
 * whose screen was renamed. Written as a plain `*Screen.kt` glob the check would have accepted
 * both.
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
                ":feature:*".module {
                    "commonMain".sourceSet / kotlin / modulePackage /
                        "${wildcards[0].pascalCase}Screen".ktFile()
                }
            }
        }
        "ViewModel" {
            title = "ViewModel"
            summary = "画面の状態を持つ androidx.lifecycle.ViewModel。" +
                "Repository から取得した値を UiState に変換し、StateFlow で公開する"
            example("HomeViewModel", "ホーム画面の状態")
            layout {
                ":feature:*".module {
                    "commonMain".sourceSet / kotlin / modulePackage /
                        "${wildcards[0].pascalCase}ViewModel".ktFile()
                }
            }
        }
        "Route" {
            title = "ルート"
            summary = "画面を navigation の Destination に結びつけ、ViewModel の生成も引き受ける"
            example("HomeRoute", "ホーム画面の遷移先")
            layout {
                ":feature:*".module {
                    "commonMain".sourceSet / kotlin / modulePackage /
                        "${wildcards[0].pascalCase}Route".ktFile()
                }
            }
        }
    }
}
