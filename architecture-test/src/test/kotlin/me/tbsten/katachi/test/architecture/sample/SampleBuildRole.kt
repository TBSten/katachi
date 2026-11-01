package me.tbsten.katachi.test.architecture.sample

import me.tbsten.katachi.dsl.DeclarationContainerScope

fun DeclarationContainerScope.sampleBuild() = "SampleBuild".role {
    title = "サンプルビルド"
    summary = "独立した Gradle ビルド4本。それぞれが自分の構成を katachi で宣言して assert している"
    description = """
        各 sample は自分の `:architecture-test` で構成を確かめているので、中身はここで宣言せず `ignore()` にしています。
        二重に宣言すると、実行されない方の写しから腐るためです。
        `ignore()` は 1 本ずつ名指ししているので、`sample/` に 4 本以外が増えると `UnexpectedDirectory` になり、
        `checkSample<Name>` タスクを足す合図になります。
        素の `./gradlew check` は sample の中のファイルを見ません。見るのは `checkSample*` です。
    """.trimIndent()
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
