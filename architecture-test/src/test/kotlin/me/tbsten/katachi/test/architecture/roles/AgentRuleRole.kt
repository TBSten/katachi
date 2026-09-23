package me.tbsten.katachi.test.architecture.roles

import me.tbsten.katachi.dsl.DeclarationContainerScope

/** The role of what Claude Code reads before it touches this repository. */
fun DeclarationContainerScope.agentRule() = "AgentRule" {
    title = "エージェント設定"
    summary = "Claude Code が読むルールとスキル"
    example("rules/kotlin.md", "docs/internal/kotlin/ の規約を読ませる入口")
    example("skills/verify-changes/SKILL.md", "変更後に何を回すかの手順")
    layout {
        ".claude" {
            "rules" / "*.md".file()
            "skills" / "*" / "SKILL.md".file()
        }
    }
}
