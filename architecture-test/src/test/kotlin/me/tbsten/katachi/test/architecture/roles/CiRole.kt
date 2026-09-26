package me.tbsten.katachi.test.architecture.roles

import me.tbsten.katachi.dsl.DeclarationContainerScope

/** The role of what CI runs: the workflows and the scripts they call. */
fun DeclarationContainerScope.ci() = "Ci" {
    title = "CI"
    summary = "GitHub Actions のワークフローと、そこから呼ばれるスクリプト"
    example("ci.yml", "check と3つのサンプルビルドを回す")
    example("check-kotlin-versions.sh", "サンプルとルートの Kotlin バージョンの一致を見る")
    example("benchmark.yml", "JMH と実プロジェクトでの計測を毎晩回し、gh-pages の履歴と比べる")
    layout {
        ".github" {
            "workflows" / "*.yml".file()
            "scripts" / "*.sh".file()
            "scripts" / "*.py".file()
        }
    }
}
