package me.tbsten.katachi.test.architecture.roles

import me.tbsten.katachi.dsl.DeclarationContainerScope

/**
 * The role of the four independent sample builds.
 *
 * ## Why the sample builds are ignored rather than declared
 *
 * `sample/jvm`, `sample/android`, `sample/kmp` and `sample/custom-processor` are independent
 * Gradle builds, and **each one already declares its own structure with katachi and asserts
 * it** from its own `:architecture-test`. Declaring their contents here as well would put the
 * same truth in two places, and the copy that nobody runs first is the one that rots.
 *
 * No hole opens up: a stray file inside a sample is found by that sample's own check, which
 * `checkSampleJvm` / `checkSampleAndroid` / `checkSampleKmp` / `checkSampleCustomProcessor`
 * run, and CI runs all four. The only difference is that a bare `./gradlew check` does not see
 * it.
 *
 * ## Why they are named one by one
 *
 * A wildcard `"*" { ignore() }` under `sample` would read the same and be wrong:
 * `LayoutIndex.isIgnored` is asked at the top of `visitDirectory`, before anything else, so it
 * would swallow anything ever put next to the samples, and whatever role declared that thing
 * would then report every file it names as `[MissingFile]`.
 */
fun DeclarationContainerScope.sampleBuild() = "SampleBuild" {
    title = "サンプルビルド"
    summary = "独立した Gradle ビルド4本。それぞれが自分の構成を katachi で宣言して assert している"
    example("sample/jvm", "Ktor のサーバサイド JVM プロジェクト")
    example("sample/android", "Android architecture guide の3層")
    example("sample/kmp", "KMP の sourceSet と feature モジュール分割")
    example("sample/custom-processor", "利用者が自分で書く processor 3本")
    layout {
        "sample" {
            "jvm".ignore()
            "android".ignore()
            "kmp".ignore()
            "custom-processor".ignore()
        }
    }
}
