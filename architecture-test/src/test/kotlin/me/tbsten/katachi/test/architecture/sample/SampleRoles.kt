package me.tbsten.katachi.test.architecture.sample

import me.tbsten.katachi.dsl.ArchitectureScope

/**
 * The roles of `sample/`.
 *
 * ## Why the three sample builds are ignored rather than declared
 *
 * `sample/jvm`, `sample/android` and `sample/kmp` are independent Gradle builds, and **each
 * one already declares its own structure with katachi and asserts it** from its own
 * `:architecture-test`. Declaring their contents here as well would put the same truth in two
 * places, and the copy that nobody runs first is the one that rots.
 *
 * No hole opens up: a stray file inside a sample is found by that sample's own check, which
 * `checkSampleJvm` / `checkSampleAndroid` / `checkSampleKmp` run, and CI runs all three. The
 * only difference is that a bare `./gradlew check` does not see it.
 *
 * ## Why the three are named one by one
 *
 * A wildcard `"*" { ignore() }` under `sample` would read the same and be wrong:
 * `LayoutIndex.isIgnored` is asked at the top of `visitDirectory`, before anything else, so it
 * would swallow `sample/layout-snapshots` too — and the role below would then report every
 * snapshot it declares as `[MissingFile]`.
 */
fun ArchitectureScope.sampleRoles() {
    "sample".group {
        title = "サンプル"

        "SampleBuild" {
            title = "サンプルビルド"
            summary = "独立した Gradle ビルド3本。それぞれが自分の構成を katachi で宣言して assert している"
            example("sample/jvm", "Ktor のサーバサイド JVM プロジェクト")
            example("sample/android", "Android architecture guide の3層")
            example("sample/kmp", "KMP の sourceSet と feature モジュール分割")
            layout {
                "sample" {
                    "jvm".ignore()
                    "android".ignore()
                    "kmp".ignore()
                }
            }
        }

        "LayoutSnapshot" {
            title = "レイアウトのスナップショット"
            summary = "各サンプルの宣言を全文書き出したもの。定義の変化を人がレビューするために差分で見せる"
            example("jvm.txt", "sample/jvm の宣言の全文")
            layout {
                "sample" / "layout-snapshots" / "*.txt".file()
            }
        }
    }
}
