package com.example.roles

import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*

/** The role of the files read at runtime — a reminder that not every role is Kotlin. */
fun DeclarationContainerScope.serverConfig() = "ServerConfig" {
    title = "サーバ設定"
    summary = "実行時に読み込まれる設定ファイル。Kotlin ではない資源も役割を持つ"
    example("application.conf", "待ち受けポートと適用するモジュール")
    example("logback.xml", "ログの出力先と書式")
    layout {
        // Listed one by one rather than with `anyFile()`: there are exactly two of
        // them, and a third one appearing is something to be told about.
        //
        // `resources` is a plain directory, not a source set and not a package: a
        // source set only ever means `src/<name>`, and what sits below it is written
        // out.
        ":".module {
            mainSourceSet {
                "resources" {
                    "application.conf".file()
                    "logback.xml".file()
                }
            }
        }
    }
}
