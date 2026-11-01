package me.tbsten.katachi.test.architecture.tool

import me.tbsten.katachi.dsl.DeclarationContainerScope

fun DeclarationContainerScope.issueTemplate() = "IssueTemplate".role {
    title = "Issue テンプレート"
    summary = "GitHub で issue を立てるときに出る入力フォームの定義"
    example("bug_report.yml", "不具合の報告フォーム")
    example("feature_request.yml", "機能の要望フォーム")
    example("config.yml", "テンプレートを選ぶ画面の設定（白紙の issue を許すか、ドキュメントへのリンク）")
    layout {
        ".github" / "ISSUE_TEMPLATE" / "*.yml".file()
    }
}
