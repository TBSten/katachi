package me.tbsten.katachi.test.architecture.tool

import me.tbsten.katachi.dsl.DeclarationContainerScope

fun DeclarationContainerScope.pullRequestTemplate() = "PullRequestTemplate".role {
    title = "PR テンプレート"
    summary = "GitHub で pull request を作るときに本文へ入る枠（チケット・影響範囲・詳細）"
    example("pull_request_template.md", "影響範囲を CONTRIBUTING の「影響範囲を書く」の形で書く枠")
    layout {
        ".github" / "pull_request_template.md".file()
    }
}
