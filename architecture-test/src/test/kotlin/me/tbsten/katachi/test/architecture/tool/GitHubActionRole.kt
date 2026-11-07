package me.tbsten.katachi.test.architecture.tool

import me.tbsten.katachi.dsl.DeclarationContainerScope

fun DeclarationContainerScope.gitHubAction() = "GitHubAction".role {
    title = "配布する GitHub Action"
    summary = "利用者のリポジトリから `TBSten/katachi/<名前>@<版>` で使う composite action と、そこから呼ぶスクリプト"
    description = """
        `uses:` のパスを短くするため、action はリポジトリ直下のディレクトリに 1 つずつ置きます。
        katachi 自身の workflow（`.github/workflows/`）もここの action を `./<名前>` で使い、
        最初の利用者と使い方の例を兼ねます。
    """.trimIndent()
    example("diff-report/action.yml", "pull request の base...head で katachiDiff を回し、コメントの Markdown を artifact に置く")
    example("diff-report-comment/action.yml", "diff-report が置いたコメントを pull request に貼り直す")
    layout {
        "diff-report" {
            "action.yml".file()
            "*.py".file()
        }
        "diff-report-comment" {
            "action.yml".file()
            "*.sh".file()
        }
    }
}
