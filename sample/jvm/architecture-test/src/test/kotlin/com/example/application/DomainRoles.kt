package com.example.application

import com.example.modulePackage
import me.tbsten.katachi.dsl.ArchitectureScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.ktFile

/** Roles of the domain layer: the behaviour and the values the application is about. */
fun ArchitectureScope.domainRoles() {
    "domain".group {
        title = "ドメイン"

        "Service" {
            title = "サービス"
            summary = "アプリ固有の振る舞いを1つ持ち、Repository を組み合わせて実現する"
            example("HealthService", "サーバの稼働状態を取得する")
            layout {
                ":".module {
                    mainSourceSet / kotlin / modulePackage / "service" / "*Service".ktFile()
                }
            }
        }

        "Model" {
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
    }
}
