package me.tbsten.katachi.test.architecture.roles

import me.tbsten.katachi.dsl.DeclarationContainerScope

/** The role of what CI runs: the workflows and the scripts they call. */
fun DeclarationContainerScope.ci() = "Ci" {
    title = "CI"
    summary = "GitHub Actions のワークフローと、そこから呼ばれるスクリプト"
    example("ci.yml", "check・サンプルのビルド・IDE プラグイン・ベンチマークを push と pull request のたびに回す")
    example("check-kotlin-versions.sh", "サンプルとルートの Kotlin バージョンの一致を見る")
    example("benchmark-pr.yml", "perf ラベルの付いた pull request を base と同じランナーで比べる")
    layout {
        ".github" {
            "workflows" / "*.yml".file()
            "scripts" / "*.sh".file()
            "scripts" / "*.py".file()
        }
    }
}
