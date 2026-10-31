package me.tbsten.katachi.test.architecture.roles

import me.tbsten.katachi.dsl.DeclarationContainerScope

/** The role of the vitest tests for the TypeScript code of the docs site that need no browser. */
fun DeclarationContainerScope.docsUnitTest() = "DocsUnitTest".role {
    title = "ドキュメントサイトのユニットテスト"
    summary = "vitest で、ブラウザを使わずにサイトの TypeScript を確かめるテスト"
    example("link-card.test.ts", "リンクカードの組み立てを確かめる")
    layout {
        "docs" / "test" / "*.test.ts".file()
    }
}
