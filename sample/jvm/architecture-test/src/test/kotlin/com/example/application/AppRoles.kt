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
            layout { }
        }

        "ServerConfig" {
            title = "サーバ設定"
            summary = "実行時に読み込まれる設定ファイル。Kotlin ではない資源も役割を持つ"
            example("application.conf", "待ち受けポートと適用するモジュール")
            example("logback.xml", "ログの出力先と書式")
            layout { }
        }
    }
}
