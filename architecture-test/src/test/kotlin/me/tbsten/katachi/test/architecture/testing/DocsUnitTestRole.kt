package me.tbsten.katachi.test.architecture.testing

import me.tbsten.katachi.dsl.DeclarationContainerScope

fun DeclarationContainerScope.docsUnitTest() = "DocsUnitTest".role {
    title = "ドキュメントサイトのユニットテスト"
    summary = "vitest で、ブラウザを使わずにサイトの TypeScript を確かめるテスト"
    example("link-card.test.ts", "リンクカードの組み立てを確かめる")
    layout {
        "docs" / "test" / "*.test.ts".file()
    }
}
