package me.tbsten.katachi.test.architecture.testing

import me.tbsten.katachi.dsl.DeclarationContainerScope

fun DeclarationContainerScope.docsE2eTest() = "DocsE2eTest".role {
    title = "ドキュメントサイトの E2E テスト"
    summary = "Playwright で dev サーバーを実際のブラウザで操作して確かめるテスト"
    example("feedback-record.spec.ts", "フィードバックの記録が保存されるまでを操作して確かめる")
    example("memo.spec.ts", "メモの追加・編集・ドラッグを操作して確かめる")
    example("playwright.config.ts", "テストが起動するサーバーとブラウザの設定")
    layout {
        "docs" {
            "e2e" {
                "*.spec.ts".file()
                // The pieces the specs share: finding elements, fixtures, pixel comparison.
                "helpers" / "*.ts".file()
                // Starts the dev server the specs talk to, and reads the environment they need.
                "*.mjs".file()
                "playwright.config.ts".file()
                "tsconfig.json".file()
            }
        }
    }
}
