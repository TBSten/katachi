package me.tbsten.katachi.test.architecture.tool

import me.tbsten.katachi.dsl.DeclarationContainerScope

fun DeclarationContainerScope.agentRule() = "AgentRule".role {
    title = "エージェント設定"
    summary = "Claude Code が読むルール"
    example("CLAUDE.md", "リポジトリ全体に効く、エージェントへの指示")
    example("rules/kotlin.md", "docs/internal/kotlin/ の規約を読ませる入口")
    layout {
        // What Claude Code reads first, at the repository root (docs/ keeps its own, as docs/CLAUDE.md).
        "CLAUDE.md".file()
        ".claude" {
            "rules" / "*.md".file()
        }
    }
}
