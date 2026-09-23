package com.example.roles

import com.example.modulePackage
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

/** The role of the values the domain is about, reused as the API payloads. */
fun DeclarationContainerScope.model() = "Model" {
    title = "モデル"
    summary = "ドメインで扱う値。API の入出力としてもそのまま使う"
    example("Health", "稼働状態とバージョン")
    layout {
        // A model is named after the thing it models, so the package is the only
        // marker. Any `.kt` directly in it counts; a subdirectory does not.
        ":".module {
            mainSourceSet / kotlin / modulePackage / "model" / "*".ktFile()
        }
    }
}
