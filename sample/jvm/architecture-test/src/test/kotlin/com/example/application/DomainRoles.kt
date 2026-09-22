package com.example.application

import com.example.modulePackage
import me.tbsten.katachi.dsl.ArchitectureScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.konsist.konsist

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
                    // Adoption step 4 of katachi's own README (`konsist-integration`): the
                    // one line that shows a backend-written constraint next to katachi's own
                    // layout vocabulary. Also stands as the regression test for
                    // `LayoutNode.synthetic` — a `":".module { }` block injects `build` and
                    // `build.gradle.kts`, and this constraint would wrongly cover
                    // `build.gradle.kts` if that exclusion ever broke.
                    "public であること".konsist {
                        classes().must { it.hasPublicOrDefaultModifier }
                    }
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
