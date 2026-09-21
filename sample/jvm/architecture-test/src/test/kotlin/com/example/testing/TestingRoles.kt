package com.example.testing

import me.tbsten.katachi.dsl.ArchitectureScope

/**
 * Roles of the test code, including the architecture definition itself.
 *
 * The definition lives in `:architecture-test`, a module that belongs to no layer of the
 * application. It is still code someone has to maintain, so it gets a role of its own
 * rather than hiding inside `Test`: `ArchitectureDefinition` describes the shape, `Test`
 * asserts behaviour.
 */
fun ArchitectureScope.testingRoles() {
    "testing".group {
        title = "テスト"

        "Test" {
            title = "テストコード"
            summary = "src/test/kotlin に置かれるテスト。本体と同じ package 構成を保つ"
            example("HealthRouteTest", "GET /health の応答を確かめる")
            example("ProjectArchitectureSpec", "この定義そのものを確かめる")
            layout {
                // `**` stands for the package levels, which mirror the main source set and are
                // not worth writing twice — so `modulePackage` is deliberately not used here.
                // The `*` after it is one file name, so a directory holding no `.kt` at all is
                // still reported.
                ":".module {
                    testSourceSet / kotlin / "**" / "*".ktFile()
                }
            }
        }

        "ArchitectureDefinition" {
            title = "アーキテクチャ定義"
            summary = "katachi の DSL で書かれた役割の定義。どのレイヤーにも属さない"
            example("ProjectArchitecture.kt", "定義の入口。各 package の拡張関数を呼ぶ")
            example("ApiRoles.kt", "API レイヤーの役割を宣言する拡張関数")
            layout {
                // The price of the recommended setup: `:architecture-test` checks itself, so
                // the definition has to give itself a role like everything else. The module
                // path resolves to `architecture-test/`, which is where the files actually are.
                ":architecture-test".module {
                    testSourceSet / kotlin / "**" / "*".ktFile()
                }
            }
        }
    }
}
