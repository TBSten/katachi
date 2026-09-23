package com.example.kmp.roles

import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

/**
 * The test code of the app itself.
 *
 * Android puts its tests in `src/test`; a KMP module puts them in `commonTest`. Both shapes
 * are one role here.
 */
fun DeclarationContainerScope.test() = "Test" {
    title = "テストコード"
    summary = "各モジュールのテスト。KMP モジュールは commonTest、" +
        "純 Android / 純 JVM モジュールは src/test"
    example("ProjectArchitectureSpec", "この定義そのものを検証するテスト")
    // Only `:app:android` has test code of its own today, and it is an Android
    // module, so `testSourceSet` is the one place declared. A KMP module would add
    // `"commonTest".sourceSet`; no module in this sample has one yet, and a path
    // declared for a directory that does not exist would claim a shape the sample
    // does not have.
    layout {
        ":app:android".module {
            testSourceSet / kotlin / "com/example/kmp/app" / "*Spec".ktFile()
        }
    }
}
