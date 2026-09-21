package com.example.application

import me.tbsten.katachi.dsl.ArchitectureScope

/** Roles that assemble and configure the running process. */
fun ArchitectureScope.appRoles() {
    "app".group {
        title = "アプリケーション"

        "Entrypoint" {
            title = "エントリポイント"
            summary = "プロセスの起動と、Ktor の Application モジュールの組み立て"
            example("Application.kt", "main() と Application.module()")
            layout {
                // No wildcard, so this one is required: delete `Application.kt` and the check
                // reports `[MissingFile]` instead of silently passing.
                "src/main/kotlin/com/example/Application".ktFile()
            }
        }

        "ServerConfig" {
            title = "サーバ設定"
            summary = "実行時に読み込まれる設定ファイル。Kotlin ではない資源も役割を持つ"
            example("application.conf", "待ち受けポートと適用するモジュール")
            example("logback.xml", "ログの出力先と書式")
            layout {
                // Listed one by one rather than with `anyFile()`: there are exactly two of
                // them, and a third one appearing is something to be told about.
                "src/main/resources" {
                    "application.conf".file()
                    "logback.xml".file()
                }
            }
        }
    }
}
