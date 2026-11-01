package me.tbsten.katachi.test.architecture.docs

import me.tbsten.katachi.dsl.DeclarationContainerScope

fun DeclarationContainerScope.internalGuide() = "InternalGuide".role {
    title = "内部向けガイド"
    summary = "エージェントと人が読む、このリポジトリの Kotlin の規約"
    example("kotlin.md", "可視性・コメント・KDoc の規約")
    example("errors.md", "例外クラスとエラーメッセージの規約")
    layout {
        // Not part of the published site: these are read from the repository, and
        // `.claude/rules/kotlin.md` points at them.
        "docs" / "internal" / "kotlin" / "*.md".file()
    }
}
