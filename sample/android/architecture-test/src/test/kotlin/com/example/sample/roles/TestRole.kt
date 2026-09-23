package com.example.sample.roles

import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

/** The role of the tests themselves, told apart from the definition by the file name. */
fun DeclarationContainerScope.test() = "Test" {
    title = "テストコード"
    summary = "各モジュールの src/test/kotlin に置くテストそのもの"
    example("ProjectArchitectureSpec", "この定義そのものを検証するテスト")
    layout {
        // `:architecture-test` is the only module of this sample with tests. The
        // app modules are checked through it, so nothing else has a `src/test`.
        //
        // Only the top level of the package: `groups/` and `roles/` hold declarations,
        // never tests, which is what `ArchitectureDefinition` says on its side.
        ":architecture-test".module {
            testSourceSet / kotlin / "com/example/sample" {
                "*Spec".ktFile()
                "*Test".ktFile()
            }
        }
    }
}
