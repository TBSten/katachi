package com.example

import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.architecture

/**
 * The architecture of this sample, described with katachi.
 *
 * Step 1 declares groups and roles only. Every `layout { }` is still an empty block: the
 * path DSL that says *where* each role's files live arrives in step 2, and the blocks are
 * stored unevaluated until then. An empty block is kept on each role on purpose, so that
 * step 2 only has to fill them in.
 */
val projectArchitecture: Architecture = architecture {
    "api".group {
        title = "API"

        "Controller" {
            title = "コントローラ"
            summary = "HTTP のリクエストを1つ受け取り、対応する Service を呼んで結果を返す"
            example("HealthController", "ヘルスチェックの結果を返す")
            layout { }
        }

        "KtorPlugin" {
            title = "Ktor プラグイン設定"
            summary = "Ktor の Application に対する横断的な設定を1つ行う"
            example("Routing", "コントローラを routing ツリーに接続する")
            example("Serialization", "JSON の content negotiation を設定する")
            layout { }
        }
    }

    "domain".group {
        title = "ドメイン"

        "Service" {
            title = "サービス"
            summary = "アプリ固有の振る舞いを1つ持ち、Repository を組み合わせて実現する"
            example("HealthService", "サーバの稼働状態を取得する")
            layout { }
        }

        "Model" {
            title = "モデル"
            summary = "ドメインで扱う値。API の入出力としてもそのまま使う"
            example("Health", "稼働状態とバージョン")
            layout { }
        }
    }

    "data".group {
        title = "データ"

        "Repository" {
            title = "リポジトリ"
            summary = "データの取得・保存を担い、取得元の詳細をドメインから隠す"
            example("HealthRepository", "稼働状態を読み出す")
            layout { }
        }
    }

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

    // Build files are checked like everything else, but they are noise in the generated
    // documentation, so the whole group opts out (D4: `documented` is an argument on a
    // group, a property inside a role block).
    "build".group(documented = false) {
        title = "ビルド"

        "Gradle" {
            title = "Gradle スクリプト"
            summary = "ビルドの定義と Gradle wrapper"
            example("build.gradle.kts", "このサンプルのビルド定義")
            example("settings.gradle.kts", "composite build と version catalog の配線")
            layout { }
        }

        "Git" {
            title = "Git 設定"
            summary = "バージョン管理の設定ファイル"
            example(".gitignore", "生成物を管理対象から外す")
            layout { }
        }
    }

    "testing".group {
        title = "テスト"

        "Test" {
            title = "テストコード"
            summary = "src/test/kotlin に置かれるテスト。本体と同じ package 構成を保つ"
            example("HealthRouteTest", "GET /health の応答を確かめる")
            example("ProjectArchitectureSpec", "この定義そのものを確かめる")
            layout { }
        }
    }
}
