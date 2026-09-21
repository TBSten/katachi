package com.example.application

import com.example.modulePackage
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
                // The application is the root project, so its module path is `":"`. What the
                // chain says is the same tree step 2 spelled out by hand: `mainSourceSet` is
                // `src/main`, `kotlin` is the directory of that name, and `modulePackage`
                // derives `com/example` from the module being evaluated.
                ":".module {
                    mainSourceSet / kotlin / modulePackage / "controller" / "*Controller".ktFile()
                }
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
                ":".module {
                    mainSourceSet / kotlin / modulePackage / "plugin" / "*".ktFile()
                }
            }
        }
    }
}
