package com.example.kmp.roles

import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

/**
 * The definition this very file is part of.
 *
 * It is not test code and belongs to no layer of the app, so it gets a role of its own instead
 * of hiding inside [test]: "a file with no role does not exist" applies to katachi's own module
 * too.
 */
fun DeclarationContainerScope.architectureDefinition() = "ArchitectureDefinition" {
    title = "アーキテクチャ定義"
    summary = ":architecture-test モジュールの src/test。katachi の DSL で書いた" +
        "このプロジェクトの定義。どのレイヤーにも属さないので専用モジュールに置く"
    example("ProjectArchitecture.kt", "定義の入口。group ごとの拡張関数を呼ぶだけ")
    example("roles/ComponentRole.kt", "Component の役割を宣言する DeclarationContainerScope 拡張関数")
    // Two patterns: the entry point and the specs sit in `com/example/kmp` itself, and each
    // concern gets one package below it — `groups`, `roles`, `processor`. The `*` in the
    // middle is that package.
    //
    // Deliberately left this loose rather than naming the two packages: a file dropped
    // into `roles` under any name still passes here. sample/android is the one that writes
    // the stricter form, and having one of each is what shows the choice exists.
    //
    // The package is written out rather than derived: `modulePackage` would turn
    // `:architecture-test` into `com/example/kmp/architectureTest`, and this module
    // deliberately holds `com.example.kmp` itself, next to nothing else.
    layout {
        ":architecture-test".module {
            testSourceSet / kotlin / "com/example/kmp" / "*".ktFile()
            testSourceSet / kotlin / "com/example/kmp" / "*" / "*".ktFile()
        }
    }
}
