package com.example.kmp.testing

import me.tbsten.katachi.dsl.ArchitectureScope

/** Test doubles, the test code itself, and the architecture definition it checks. */
fun ArchitectureScope.testingRoles() {
    "testing".group {
        title = "テスト支援"

        "Fake" {
            title = "フェイク"
            summary = ":testing の commonMain に置く偽の実装。他モジュールのテストから使う"
            example("FakeUserRepository", "UserRepository の偽実装")
            layout { }
        }
        // Android puts its tests in `src/test`; a KMP module puts them in `commonTest`.
        // Both shapes are one role here.
        "Test" {
            title = "テストコード"
            summary = "各モジュールのテスト。KMP モジュールは commonTest、" +
                "純 Android / 純 JVM モジュールは src/test"
            example("ProjectArchitectureSpec", "この定義そのものを検証するテスト")
            layout { }
        }
        // The definition this very file is part of. It is not test code and belongs to no
        // layer of the app, so it gets a role of its own instead of hiding inside `Test`:
        // "a file with no role does not exist" applies to katachi's own module too.
        "ArchitectureDefinition" {
            title = "アーキテクチャ定義"
            summary = ":architecture-test モジュールの src/test。katachi の DSL で書いた" +
                "このプロジェクトの定義。どのレイヤーにも属さないので専用モジュールに置く"
            example("ProjectArchitecture.kt", "定義の入口。役割ごとの拡張関数を呼ぶだけ")
            example("UiRoles.kt", "ui group の役割を宣言する ArchitectureScope 拡張関数")
            layout { }
        }
    }
}
