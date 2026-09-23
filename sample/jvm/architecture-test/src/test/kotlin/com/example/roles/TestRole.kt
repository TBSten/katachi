package com.example.roles

import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

/** The role of code that asserts behaviour, mirroring the main source set. */
fun DeclarationContainerScope.test() = "Test" {
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
