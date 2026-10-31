package me.tbsten.katachi.test.architecture.roles

import me.tbsten.katachi.dsl.DeclarationContainerScope

/**
 * The role of the Playwright tests that drive the docs site in a browser.
 *
 * Covers the feedback mode and the memo, which only exist in the dev server and so cannot be
 * checked from the built site. The dev server launcher, the environment and the Playwright config
 * live beside the specs because the specs cannot run without them.
 */
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
