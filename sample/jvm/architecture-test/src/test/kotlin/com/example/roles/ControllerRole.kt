package com.example.roles

import com.example.modulePackage
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

/** The role that faces HTTP: one request in, one service call, one response out. */
fun DeclarationContainerScope.controller() = "Controller" {
    title = "コントローラ"
    summary = "HTTP のリクエストを1つ受け取り、対応する Service を呼んで結果を返す"
    example("HealthController", "ヘルスチェックの結果を返す")
    layout {
        // The application is the root project, so its module path is `":"`. What the
        // chain says is the same tree step 2 spelled out by hand: `mainSourceSet` is
        // `src/main`, `kotlin` is the directory of that name, and `modulePackage`
        // derives `com/example` from the module being evaluated.
        ":".module {
            mainSourceSet / kotlin / modulePackage / "controller" / "*Controller".ktFile()
        }
    }
}
