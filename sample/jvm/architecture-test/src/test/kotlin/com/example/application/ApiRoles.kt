package com.example.application

import me.tbsten.katachi.dsl.ArchitectureScope

/**
 * Roles of the API layer: everything that faces HTTP.
 *
 * Declared as an extension on [ArchitectureScope] so that the definition can be split
 * across files. It must not be `inline`: an inlined frame reports a line number past the
 * end of the caller's file, and the captured declaration site would be wrong.
 */
fun ArchitectureScope.apiRoles() {
    "api".group {
        title = "API"

        "Controller" {
            title = "コントローラ"
            summary = "HTTP のリクエストを1つ受け取り、対応する Service を呼んで結果を返す"
            example("HealthController", "ヘルスチェックの結果を返す")
            layout {
                // Step 2 spells every level out as a plain directory. `src/main` is not yet
                // `mainSourceSet` and `com/example` is not yet `modulePackage`: those arrive in
                // step 3, and the check has to give the same answer afterwards.
                "src" / "main" / "kotlin" / "com" / "example" / "controller" / "*Controller".ktFile()
            }
        }

        "KtorPlugin" {
            title = "Ktor プラグイン設定"
            summary = "Ktor の Application に対する横断的な設定を1つ行う"
            example("Routing", "コントローラを routing ツリーに接続する")
            example("Serialization", "JSON の content negotiation を設定する")
            layout {
                // No suffix to key on: a plugin file is named after the Ktor feature it
                // installs, so the package itself is what says "this is a plugin".
                "src" / "main" / "kotlin" / "com" / "example" / "plugin" / "*".ktFile()
            }
        }
    }
}
