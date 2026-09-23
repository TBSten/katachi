package com.example.roles

import com.example.modulePackage
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

/** The role of one cross-cutting setting installed into the Ktor `Application`. */
fun DeclarationContainerScope.ktorPlugin() = "KtorPlugin" {
    title = "Ktor プラグイン設定"
    summary = "Ktor の Application に対する横断的な設定を1つ行う"
    example("Routing", "コントローラを routing ツリーに接続する")
    example("Serialization", "JSON の content negotiation を設定する")
    layout {
        // No suffix to key on: a plugin file is named after the Ktor feature it
        // installs, so the package itself is what says "this is a plugin".
        ":".module {
            mainSourceSet / kotlin / modulePackage / "plugin" / "*".ktFile()
        }
    }
}
