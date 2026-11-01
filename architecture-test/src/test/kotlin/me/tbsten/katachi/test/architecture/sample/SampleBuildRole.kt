package me.tbsten.katachi.test.architecture.sample

import me.tbsten.katachi.dsl.DeclarationContainerScope

fun DeclarationContainerScope.sampleBuild() = "SampleBuild".role {
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
