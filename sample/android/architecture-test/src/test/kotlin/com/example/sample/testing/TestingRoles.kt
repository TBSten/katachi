package com.example.sample.testing

import me.tbsten.katachi.dsl.ArchitectureScope

/**
 * Roles that exist for testing: the shared fakes in `:testing`, the tests themselves, and
 * the architecture definition.
 *
 * The definition lives in `:architecture-test`, a module that belongs to no layer of the
 * application. It is still code someone has to maintain, so it gets a role of its own
 * rather than hiding inside `Test`: `ArchitectureDefinition` describes the shape, `Test`
 * asserts behaviour.
 *
 * Deliberately not `inline`. katachi reads the declaration site off the stack trace, and an
 * inlined frame reports a line number remapped past the end of the caller's file.
 */
fun ArchitectureScope.testingRoles() {
    "testing".group {
        title = "テスト"

        "Fake" {
            title = "フェイク"
            summary = ":testing に置く、他モジュールのテストから使う偽の実装"
            example("FakeUserRepository", "UserRepository のメモリ実装")
            layout { }
        }

        "Test" {
            title = "テストコード"
            summary = "各モジュールの src/test/kotlin に置くテストそのもの"
            example("ProjectArchitectureSpec", "この定義そのものを検証するテスト")
            layout { }
        }

        "ArchitectureDefinition" {
            title = "アーキテクチャ定義"
            summary = "katachi の DSL で書かれた役割の定義。どのレイヤーにも属さない"
            example("ProjectArchitecture.kt", "定義の入口。各 package の拡張関数を呼ぶ")
            example("UiRoles.kt", "UI レイヤーの役割を宣言する拡張関数")
            layout { }
        }
    }
}
